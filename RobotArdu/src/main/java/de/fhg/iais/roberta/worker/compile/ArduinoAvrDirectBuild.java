package de.fhg.iais.roberta.worker.compile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import de.fhg.iais.roberta.util.basic.Pair;
import de.fhg.iais.roberta.util.Util;

/**
 * Builds a program for the AVR based Arduino boards (Uno, Nano, Mega) by calling avr-g++, avr-gcc and avr-objcopy directly, i.e. without the bash
 * script <code>arduino-resources/build_project.sh</code>. The steps are the same as in that script.<br>
 * <br>
 * This is used on Windows (no bash available, and <code>cmd /c</code> breaks on paths with blanks). The AVR toolchain is taken from
 * <code>arduino-resources/avr-gcc/bin</code>, if that directory exists, otherwise from the PATH.
 */
final class ArduinoAvrDirectBuild {
    private static final List<String> SUPPORTED_ROBOTS = Arrays.asList("uno", "nano", "mega");

    private ArduinoAvrDirectBuild() {
    }

    static boolean isSupported(String robot, String arduinoArch) {
        return "avr".equals(arduinoArch) && SUPPORTED_ROBOTS.contains(robot);
    }

    /**
     * @return the success flag and the output of the failing (or last) tool
     */
    static Pair<Boolean, String> build(
        String compilerResourcesDir,
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
        String includes = path(resources, "includes");
        String libDir = path(resources, "lib", robot);
        String coreFile = path(resources, "core", robot, "core.a");
        String coreIncludes = path(resources, "hardware", "arduino", arduinoArch, "cores", "arduino");
        String variantIncludes = path(resources, "hardware", "arduino", arduinoArch, "variants", boardVariant);
        File toolDir = new File(resources, "avr-gcc" + File.separator + "bin");

        String source = path(sourceDir, programName + ".cpp");
        String object = path(sourceDir, programName + ".o");
        String elf = path(sourceDir, programName + ".elf");
        String eep = path(sourceDir, programName + ".eep");
        String hex = path(targetDir, programName + ".hex");
        new File(targetDir).mkdirs();
        new File(hex).delete();

        List<String> compile = new ArrayList<>();
        compile.add(tool(toolDir, "avr-g++"));
        compile.addAll(Arrays.asList("-c", "-g", "-Os", "-Wall", "-Wextra", "-std=gnu++11", "-fpermissive", "-fno-exceptions", "-ffunction-sections",
            "-fdata-sections", "-fno-threadsafe-statics", "-Wno-error=narrowing", "-MMD", "-mmcu=" + mmcu, "-DF_CPU=16000000L", "-DARDUINO=10810",
            "-D" + arduinoVariant, "-DARDUINO_ARCH_" + arduinoArch.toUpperCase(Locale.ROOT)));
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
                coreIncludes,
                variantIncludes) ) {
            compile.add("-I" + include);
        }
        compile.addAll(Arrays.asList(source, "-o", object));

        List<String> link =
            Arrays
                .asList(
                    tool(toolDir, "avr-gcc"),
                    "-Wall",
                    "-Wextra",
                    "-Os",
                    "-g",
                    "-Wl,--gc-sections,--relax",
                    "-mmcu=" + mmcu,
                    "-o",
                    elf,
                    object,
                    "-L" + libDir,
                    "-lora",
                    "-lm",
                    coreFile);
        List<String> eeprom =
            Arrays
                .asList(
                    tool(toolDir, "avr-objcopy"),
                    "-O",
                    "ihex",
                    "-j",
                    ".eeprom",
                    "--set-section-flags=.eeprom=alloc,load",
                    "--no-change-warnings",
                    "--change-section-lma",
                    ".eeprom=0",
                    elf,
                    eep);
        List<String> flash = Arrays.asList(tool(toolDir, "avr-objcopy"), "-O", "ihex", "-R", ".eeprom", elf, hex);

        Pair<Boolean, String> result = Pair.of(false, "");
        try {
            for ( List<String> step : Arrays.asList(compile, link, eeprom, flash) ) {
                result = Util.runCrossCompiler(step.toArray(new String[0]), crosscompilerSource, isNativeEditorCode);
                if ( !result.getFirst() ) {
                    return result;
                }
            }
            return result;
        } finally {
            for ( String intermediate : Arrays.asList(object, elf, eep, path(sourceDir, programName + ".d")) ) {
                new File(intermediate).delete();
            }
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
