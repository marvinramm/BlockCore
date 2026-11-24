package de.fhg.iais.roberta.worker.compile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ArduinoCompilerWorkerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void storeBundledHeader_copiesL293DMotorHeaderIntoGeneratedSourceDirectory() throws Exception {
        Path sourceDir = this.temporaryFolder.newFolder("source").toPath();

        ArduinoCompilerWorker.storeBundledHeader(sourceDir, "/arduino/L293DMotor/L293DMotor.h", "L293DMotor/L293DMotor.h");

        Path copiedHeader = sourceDir.resolve("L293DMotor/L293DMotor.h");
        Assert.assertTrue(Files.isRegularFile(copiedHeader));
        String header = new String(Files.readAllBytes(copiedHeader), StandardCharsets.UTF_8);
        Assert.assertTrue(header.contains("class L293DMotor"));
        Assert.assertTrue(header.contains("void run(int speed) const"));
    }

    @Test
    public void storeBundledHeader_copiesTcs34725HeaderIntoGeneratedSourceDirectory() throws Exception {
        Path sourceDir = this.temporaryFolder.newFolder("source").toPath();

        ArduinoCompilerWorker.storeBundledHeader(sourceDir, "/arduino/TCS34725/TCS34725.h", "TCS34725/TCS34725.h");

        Path copiedHeader = sourceDir.resolve("TCS34725/TCS34725.h");
        Assert.assertTrue(Files.isRegularFile(copiedHeader));
        String header = new String(Files.readAllBytes(copiedHeader), StandardCharsets.UTF_8);
        Assert.assertTrue(header.contains("class TCS34725"));
        Assert.assertTrue(header.contains("T colour(T red, T green, T white, T black, T none)"));
    }
}
