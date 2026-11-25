package de.fhg.iais.roberta.worker.compile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.util.Util;
import de.fhg.iais.roberta.util.basic.Pair;

/**
 * Builds a program for the AVR based Arduino boards (Uno, Nano, Mega) by calling avr-g++, avr-gcc and avr-objcopy directly, i.e. without the bash
 * script <code>arduino-resources/build_project.sh</code>.<br>
 * <br>
 * The AVR toolchain is taken from <code>arduino-resources/avr-gcc/bin</code>, if that directory exists, otherwise from the PATH.<br>
 * <br>
 * Speed: every generated program includes <code>Arduino.h</code> and <code>NEPODefs.h</code>, which pulls in the ArduinoSTL, more than 2000
 * header files. These headers are compiled once per board into a precompiled header (<code>blockcore_pch.h.gch</code> in the temporary
 * directory). A program is compiled with it in a fraction of the time. The precompiled header is created in the background after the first
 * program of a board was built. If gcc cannot use it (other compiler, other options), gcc reads the headers as usual: the result is the same.
 */
final class ArduinoAvrDirectBuild {
    private static final Logger LOG = LoggerFactory.getLogger(ArduinoAvrDirectBuild.class);
    private static final List<String> SUPPORTED_ROBOTS = Arrays.asList("uno", "nano", "mega");
    static final String PCH_HEADER = "blockcore_pch.h";
    /** the headers of {@link #PCH_HEADER}. All have include guards, all are included by every generated program (directly or by NEPODefs.h) */
    static final String PCH_CONTENT = "#include <Arduino.h>\n#include <ArduinoSTL.h>\n#include <list>\n#include <math.h>\n#include <vector>\n";
    private static final Set<String> PCH_IN_PROGRESS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService PCH_CREATOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "precompiled-header");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private ArduinoAvrDirectBuild() {
    }

    static boolean isSupported(String robot, String arduinoArch) {
        return "avr".equals(arduinoArch) && SUPPORTED_ROBOTS.contains(robot);
    }

    /**
     * @param tempDir the temporary directory of the server: the precompiled headers are stored there
     * @return the success flag and the output of the failing (or last) tool
     */
    static Pair<Boolean, String> build(
        String compilerResourcesDir,
        String tempDir,
        String robot,
        String boardVariant,
        String mmcu,
        String arduinoVariant,
        String arduinoArch,
        String sourceDir,
        String targetDir,
        String programName,
        String crosscompilerSource,
        boolean isNativeEditorCode) //
    {
        String resources = new File(compilerResourcesDir, "arduino-resources").getAbsolutePath();
        String libDir = path(resources, "lib", robot);
        String coreFile = path(resources, "core", robot, "core.a");
        File toolDir = new File(resources, "avr-gcc" + File.separator + "bin");

        String source = path(sourceDir, programName + ".cpp");
        String object = path(sourceDir, programName + ".o");
        String elf = path(sourceDir, programName + ".elf");
        String hex = path(targetDir, programName + ".hex");
        new File(targetDir).mkdirs();
        new File(hex).delete();

        List<String> options = compileOptions(resources, boardVariant, mmcu, arduinoVariant, arduinoArch);
        File pchDir = new File(tempDir, "blockcore-pch" + File.separator + robot + "-" + fingerprint(tool(toolDir, "avr-g++"), options));
        boolean pchUsable = usesPchHeaders(crosscompilerSource);
        boolean pchReady = pchUsable && new File(pchDir, PCH_HEADER + ".gch").isFile();

        List<String> compile = new ArrayList<>();
        compile.add(tool(toolDir, "avr-g++"));
        compile.add("-c");
        if ( pchReady ) {
            compile.add("-include");
            compile.add(new File(pchDir, PCH_HEADER).getAbsolutePath());
        }
        compile.addAll(options);
        compile.addAll(Arrays.asList(source, "-o", object));

        List<String> link =
            Arrays.asList(tool(toolDir, "avr-gcc"), "-Os", "-Wl,--gc-sections,--relax", "-mmcu=" + mmcu, "-o", elf, object, "-L" + libDir, "-lora", "-lm", coreFile);
        List<String> flash = Arrays.asList(tool(toolDir, "avr-objcopy"), "-O", "ihex", "-R", ".eeprom", elf, hex);

        Pair<Boolean, String> result = Pair.of(false, "");
        try {
            for ( List<String> step : Arrays.asList(compile, link, flash) ) {
                result = Util.runCrossCompiler(step.toArray(new String[0]), crosscompilerSource, isNativeEditorCode);
                if ( !result.getFirst() ) {
                    return result;
                }
            }
            if ( pchUsable && !pchReady ) {
                createPrecompiledHeaderLater(pchDir, tool(toolDir, "avr-g++"), options);
            }
            return result;
        } finally {
            for ( String intermediate : Arrays.asList(object, elf) ) {
                new File(intermediate).delete();
            }
        }
    }

    /**
     * the options of avr-g++ (without -c, the input and the output). The program and the precompiled header must be compiled with the same options.
     */
    static List<String> compileOptions(String resources, String boardVariant, String mmcu, String arduinoVariant, String arduinoArch) {
        String includes = path(resources, "includes");
        List<String> options = new ArrayList<>();
        options
            .addAll(
                Arrays
                    .asList(
                        "-Os",
                        "-Wall",
                        "-Wextra",
                        "-std=gnu++11",
                        "-fpermissive",
                        "-fno-exceptions",
                        "-ffunction-sections",
                        "-fdata-sections",
                        "-fno-threadsafe-statics",
                        "-Wno-error=narrowing",
                        "-mmcu=" + mmcu,
                        "-DF_CPU=16000000L",
                        "-DARDUINO=10810",
                        "-D" + arduinoVariant,
                        "-DARDUINO_ARCH_" + arduinoArch.toUpperCase(Locale.ROOT)));
        for ( String include : Arrays
            .asList(
                includes,
                path(includes, "RobertaFunctions"),
                path(includes, "ArduinoSTL", "src"),
                path(includes, arduinoArch, "EEPROM", "src"),
                path(includes, arduinoArch, "HID", "src"),
                path(includes, arduinoArch, "SoftwareSerial", "src"),
                path(includes, arduinoArch, "SPI", "src"),
                path(includes, arduinoArch, "Wire", "src"),
                path(includes, "Adafruit_GFX_Library"),
                path(includes, "Adafruit_SSD1306"),
                path(includes, "Adafruit_BusIO"),
                path(resources, "hardware", "arduino", arduinoArch, "cores", "arduino"),
                path(resources, "hardware", "arduino", arduinoArch, "variants", boardVariant)) ) {
            options.add("-I" + include);
        }
        return options;
    }

    /** the precompiled header contains the ArduinoSTL: only programs that include it (all generated programs do, by NEPODefs.h) may use it */
    static boolean usesPchHeaders(String source) {
        return source.contains("#include <Arduino.h>") && source.contains("#include <NEPODefs.h>") && !source.contains("_ARDUINO_STL_NOT_NEEDED");
    }

    private static void createPrecompiledHeaderLater(File pchDir, String compiler, List<String> options) {
        String key = pchDir.getAbsolutePath();
        if ( !PCH_IN_PROGRESS.add(key) ) {
            return;
        }
        PCH_CREATOR.execute(() -> {
            try {
                createPrecompiledHeader(pchDir, compiler, options);
            } finally {
                PCH_IN_PROGRESS.remove(key);
            }
        });
    }

    /**
     * creates <code>blockcore_pch.h</code> and <code>blockcore_pch.h.gch</code> in the given directory. The .gch file is written under another name
     * first and then renamed: a build running at the same time never sees half a file.
     *
     * @return true, if the precompiled header was created
     */
    static boolean createPrecompiledHeader(File pchDir, String compiler, List<String> options) {
        File header = new File(pchDir, PCH_HEADER);
        File gch = new File(pchDir, PCH_HEADER + ".gch");
        File gchPart = new File(pchDir, PCH_HEADER + ".gch.part");
        try {
            Files.createDirectories(pchDir.toPath());
            Files.write(header.toPath(), PCH_CONTENT.getBytes(StandardCharsets.US_ASCII));
            List<String> command = new ArrayList<>();
            command.add(compiler);
            command.add("-x");
            command.add("c++-header");
            command.addAll(options);
            command.add(header.getAbsolutePath());
            command.add("-o");
            command.add(gchPart.getAbsolutePath());
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if ( process.waitFor() != 0 || !gchPart.isFile() ) {
                LOG.warn("the precompiled header {} could not be created", gch);
                gchPart.delete();
                return false;
            }
            Files.move(gchPart.toPath(), gch.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("precompiled header created: {}", gch);
            return true;
        } catch ( IOException e ) {
            LOG.warn("the precompiled header " + gch + " could not be created", e);
            gchPart.delete();
            return false;
        } catch ( InterruptedException e ) {
            Thread.currentThread().interrupt();
            gchPart.delete();
            return false;
        }
    }

    /** a short hash of the compiler (path, size, date) and its options: a new toolchain or other options get a new precompiled header */
    static String fingerprint(String compiler, List<String> options) {
        File compilerFile = new File(compiler);
        StringBuilder text = new StringBuilder(compiler).append('|').append(compilerFile.length()).append('|').append(compilerFile.lastModified());
        for ( String option : options ) {
            text.append('|').append(option);
        }
        text.append('|').append(PCH_CONTENT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexDigest = new StringBuilder();
            for ( int i = 0; i < 6; i++ ) {
                hexDigest.append(String.format("%02x", digest[i]));
            }
            return hexDigest.toString();
        } catch ( NoSuchAlgorithmException e ) {
            return Integer.toHexString(text.toString().hashCode());
        }
    }

    private static String tool(File toolDir, String name) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        File tool = new File(toolDir, windows ? name + ".exe" : name);
        return tool.isFile() ? tool.getAbsolutePath() : name;
    }

    private static String path(String first, String... more) {
        File file = new File(first);
        for ( String part : more ) {
            file = new File(file, part);
        }
        return file.getAbsolutePath();
    }
}
