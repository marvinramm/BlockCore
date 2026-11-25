package de.fhg.iais.roberta.worker.compile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

import de.fhg.iais.roberta.bean.CompilerSetupBean;
import de.fhg.iais.roberta.components.Project;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.Util;
import de.fhg.iais.roberta.util.ZipHelper;
import de.fhg.iais.roberta.util.basic.Pair;
import de.fhg.iais.roberta.util.dbc.DbcException;
import de.fhg.iais.roberta.visitor.codegen.NIBOHexPrefix;
import de.fhg.iais.roberta.worker.ICompilerWorker;

public class ArduinoCompilerWorker implements ICompilerWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ArduinoCompilerWorker.class);
    /** drivers of BlockCore that are copied next to the generated program if it includes them: "L293DMotor/L293DMotor.h", ... */
    static final List<String> BUNDLED_HEADERS = Arrays.asList("L293DMotor/L293DMotor.h", "TCS34725/TCS34725.h", "MPU6050/MPU6050.h", "DS18B20/DS18B20.h");

    @Override
    public void execute(Project project) {
        CompilerSetupBean compilerWorkflowBean = project.getWorkerResult(CompilerSetupBean.class);
        // a program is built in <tempDir>/<token>/<programName>. Two builds of the same program at the same time (e.g. the run button was
        // clicked twice) would overwrite and delete each other's intermediate files: they are run one after the other.
        String buildDirectory = compilerWorkflowBean.getTempDir() + project.getToken() + "/" + project.getProgramName();
        synchronized ( lockFor(buildDirectory) ) {
            build(project, compilerWorkflowBean);
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Object> BUILD_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Object lockFor(String buildDirectory) {
        return BUILD_LOCKS.computeIfAbsent(buildDirectory, key -> new Object());
    }

    private void build(Project project, CompilerSetupBean compilerWorkflowBean) {
        String compilerResourcesDir = compilerWorkflowBean.getCompilerResourcesDir();
        String tempDir = compilerWorkflowBean.getTempDir();
        String programName = project.getProgramName();
        String token = project.getToken();
        final String crosscompilerSource = project.getSourceCodeBuilder().toString();
        Util.storeGeneratedProgram(tempDir, crosscompilerSource, token, programName, "." + project.getSourceCodeFileExtension());
        String scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project.sh");

        String boardVariant = "";
        String mmcu = "NOTUSED"; // important for Windows
        String arduinoVariant = "";
        String arduinoArch = "";
        String cmd = "";
        String cmdopt1 = "";

        switch ( project.getRobot() ) {
            case "uno":
            case "nano":
                boardVariant = "standard";
                mmcu = "atmega328p";
                arduinoVariant = "ARDUINO_AVR_" + project.getRobot().toUpperCase();
                arduinoArch = "avr";
                break;
            case "mbot":
                boardVariant = "standard";
                mmcu = "atmega328p";
                arduinoVariant = "ARDUINO_AVR_UNO";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_mbot.sh");
                arduinoArch = "avr";
                break;
            case "botnroll":
                boardVariant = "standard";
                mmcu = "atmega328p";
                arduinoVariant = "ARDUINO_AVR_UNO";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_botnroll.sh");
                arduinoArch = "avr";
                break;
            case "unowifirev2":
                boardVariant = "uno2018";
                mmcu = "atmega4809";
                arduinoVariant = "ARDUINO_AVR_UNO_WIFI_REV2";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_unowifirev2.sh");
                arduinoArch = "megaavr";
                break;
            case "mega":
                boardVariant = "mega";
                mmcu = "atmega2560";
                arduinoVariant = "ARDUINO_AVR_MEGA2560";
                arduinoArch = "avr";
                break;
            case "sensebox":
                boardVariant = "sensebox_mcu";
                arduinoVariant = "ARDUINO_SAMD_MKR1000";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_sensebox.sh");
                arduinoArch = "samd";
                break;
            case "bob3":
                mmcu = "atmega88";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_bob3.sh");
                break;
            case "rob3rta":
                mmcu = "atmega328pb";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_rob3rta.sh");
                break;
            case "festobionic":
            case "festobionicflower":
                boardVariant = "esp32";
                arduinoVariant = "ARDUINO_ESP32_DEV";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_" + project.getRobot() + ".sh");
                arduinoArch = "esp32";
                break;
            case "nano33ble":
                boardVariant = "nano_33_iot";
                arduinoVariant = "ARDUINO_ARDUINO_NANO33BLE";
                scriptName = Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/build_project_nano33ble.sh");
                arduinoArch = "mbed";
                break;
            default:
                throw new DbcException("Unsupported Arduino type: " + project.getRobot());
        }

        String sourceDir = tempDir + token + "/" + programName + "/source/";
        String targetDir = tempDir + token + "/" + programName + "/target/";
        storeBundledHeaders(Paths.get(sourceDir), crosscompilerSource);
        ArrayList<String> parameters = new ArrayList<String>();

        if ( SystemUtils.IS_OS_WINDOWS ) {
            cmd = "cmd.exe";
            cmdopt1 = "/c";
            parameters.add(cmd);
            parameters.add(cmdopt1);
        }
        parameters.add(scriptName);
        parameters.add(boardVariant);
        parameters.add(mmcu);
        parameters.add(arduinoVariant);
        parameters.add(sourceDir);
        parameters.add(programName);
        parameters.add(compilerResourcesDir);
        parameters.add(project.getRobot());
        parameters.add(arduinoArch);

        String[] executableWithParameters = parameters.stream().toArray(String[]::new);
        Pair<Boolean, String> result;

        if ( useDirectBuild(project.getRobot(), arduinoArch) ) {
            // no bash on Windows: run the AVR toolchain directly (see ArduinoAvrDirectBuild)
            result =
                ArduinoAvrDirectBuild
                    .build(
                        compilerResourcesDir,
                        project.getRobot(),
                        boardVariant,
                        mmcu,
                        arduinoVariant,
                        arduinoArch,
                        sourceDir,
                        targetDir,
                        programName,
                        crosscompilerSource,
                        project.isNativeEditorCode());
        } else {
            result = Util.runCrossCompiler(executableWithParameters, crosscompilerSource, project.isNativeEditorCode());
        }

        Key resultKey = result.getFirst() ? Key.COMPILERWORKFLOW_SUCCESS : Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED;
        if ( result.getFirst() ) {
            String base64EncodedHex = null, optHexPrefix = null;
            switch ( project.getBinaryFileExtension() ) {
                case "hex":
                    try {
                        optHexPrefix = NIBOHexPrefix.getHexPrefixForRobot(project.getRobot());
                    } catch ( IllegalArgumentException e ) {
                        LOG.warn(e.getMessage()); // no hex prefix needed for this robot
                    }
                    base64EncodedHex = Util.getBase64EncodedHex(targetDir + programName + "." + project.getBinaryFileExtension(), optHexPrefix);
                    break;
                case "bin":
                    base64EncodedHex = Util.getBase64EncodedBinary(targetDir + programName + "." + project.getBinaryFileExtension());
                    break;
                case "zip":
                    // as we currently only support sending a single file to robots and esp32 needs two files, they are zipped and send the zip is sent to the Connector
                    // the connector then unzips the files and correctly flashes them to the robot
                    // in order to circumvent large changes
                    try {
                        ZipHelper
                            .zipFiles(
                                Stream
                                    .concat(
                                        Files.walk(Paths.get(targetDir)).filter(Files::isRegularFile),
                                        Stream
                                            .of(
                                                Paths.get(Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/hardware/esp32/esp32/tools/sdk/bin/bootloader_qio_80m.bin")),
                                                Paths.get(Util.getOsSpecificAbsolutePath(compilerResourcesDir + "arduino-resources/hardware/esp32/esp32/tools/partitions/boot_app0.bin"))))
                                    .collect(Collectors.toList()),
                                Paths.get(targetDir, programName + "." + project.getBinaryFileExtension()));
                        base64EncodedHex =
                            FileUtils.readFileToString(Paths.get(targetDir + programName + "." + project.getBinaryFileExtension()).toFile(), Charsets.UTF_8);
                    } catch ( IOException e ) {
                        LOG.warn("The generated esp32 build files could not be zipped:", e);
                    }
                    break;
                default:
                    throw new DbcException("Unsupported file extension: " + project.getRobot());
            }
            project.setCompiledHex(base64EncodedHex);
            if ( project.getCompiledHex() != null ) {
                resultKey = Key.COMPILERWORKFLOW_SUCCESS;
            } else {
                resultKey = Key.COMPILERWORKFLOW_ERROR_PROGRAM_COMPILE_FAILED;
            }
        }
        project.setResult(resultKey);
        project.addResultParam("MESSAGE", result.getSecond());
        String robot = project.getRobot();
        if ( resultKey == Key.COMPILERWORKFLOW_SUCCESS ) {
            LOG.info("compile {} program {} successful", robot, programName);
        } else {
            LOG.error("compile {} program {} failed with {}", robot, programName, result.getSecond());
        }
    }

    /**
     * The AVR boards are built without bash on Windows. Setting the system property <code>ora.arduino.directBuild=true</code> enables this on other
     * systems, too (used for tests).
     */
    static boolean useDirectBuild(String robot, String arduinoArch) {
        return ArduinoAvrDirectBuild.isSupported(robot, arduinoArch) && (SystemUtils.IS_OS_WINDOWS || Boolean.getBoolean("ora.arduino.directBuild"));
    }

    static void storeBundledHeaders(Path sourceDir, String crosscompilerSource) {
        for ( String header : BUNDLED_HEADERS ) {
            if ( crosscompilerSource.contains("#include \"" + header + "\"") ) {
                storeBundledHeader(sourceDir, "/arduino/" + header, header);
            }
        }
    }

    static void storeBundledHeader(Path sourceDir, String resourcePath, String targetPath) {
        Path headerPath = sourceDir.resolve(targetPath);
        try ( InputStream headerStream = ArduinoCompilerWorker.class.getResourceAsStream(resourcePath) ) {
            if ( headerStream == null ) {
                throw new DbcException("Bundled Arduino header is missing: " + resourcePath);
            }
            Files.createDirectories(headerPath.getParent());
            Files.copy(headerStream, headerPath, StandardCopyOption.REPLACE_EXISTING);
        } catch ( IOException e ) {
            String message = "Could not store bundled Arduino header: " + targetPath;
            LOG.error(message, e);
            throw new DbcException(message, e);
        }
    }
}
