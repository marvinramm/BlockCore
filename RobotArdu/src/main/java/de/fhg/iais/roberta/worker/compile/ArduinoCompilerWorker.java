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

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.bean.CompilerSetupBean;
import de.fhg.iais.roberta.components.Project;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.Util;
import de.fhg.iais.roberta.util.basic.Pair;
import de.fhg.iais.roberta.util.dbc.DbcException;
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
            case "mega":
                boardVariant = "mega";
                mmcu = "atmega2560";
                arduinoVariant = "ARDUINO_AVR_MEGA2560";
                arduinoArch = "avr";
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
            // run the AVR toolchain directly, with a precompiled header (see ArduinoAvrDirectBuild)
            result =
                ArduinoAvrDirectBuild
                    .build(
                        compilerResourcesDir,
                        tempDir,
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
            String base64EncodedHex = Util.getBase64EncodedHex(targetDir + programName + ".hex", null);
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
     * The AVR boards are built without the bash script build_project.sh (no bash on Windows, faster everywhere). The system property
     * <code>ora.arduino.directBuild=false</code> selects the script again.
     */
    static boolean useDirectBuild(String robot, String arduinoArch) {
        return ArduinoAvrDirectBuild.isSupported(robot, arduinoArch) && !"false".equals(System.getProperty("ora.arduino.directBuild"));
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
