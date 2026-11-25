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

    @Test
    public void storeBundledHeaders_copiesOnlyTheIncludedDrivers() throws Exception {
        Path sourceDir = this.temporaryFolder.newFolder("source").toPath();
        String program = "#include <Arduino.h>\n#include \"MPU6050/MPU6050.h\"\n#include \"DS18B20/DS18B20.h\"\n";

        ArduinoCompilerWorker.storeBundledHeaders(sourceDir, program);

        Assert.assertTrue(new String(Files.readAllBytes(sourceDir.resolve("MPU6050/MPU6050.h")), StandardCharsets.UTF_8).contains("class MPU6050"));
        Assert.assertTrue(new String(Files.readAllBytes(sourceDir.resolve("DS18B20/DS18B20.h")), StandardCharsets.UTF_8).contains("class DS18B20"));
        Assert.assertFalse(Files.exists(sourceDir.resolve("TCS34725/TCS34725.h")));
        Assert.assertFalse(Files.exists(sourceDir.resolve("L293DMotor/L293DMotor.h")));
    }

    @Test
    public void precompiledHeader_isUsedOnlyForProgramsWithTheNepoHeaders() {
        Assert.assertTrue(ArduinoAvrDirectBuild.usesPchHeaders("#include <Arduino.h>\n#include <Wire.h>\n#include <NEPODefs.h>\nvoid setup() {}"));
        Assert.assertFalse(ArduinoAvrDirectBuild.usesPchHeaders("#include <Arduino.h>\nvoid setup() {}"));
        Assert.assertFalse(ArduinoAvrDirectBuild.usesPchHeaders("#define _ARDUINO_STL_NOT_NEEDED\n#include <Arduino.h>\n#include <NEPODefs.h>\n"));
    }

    @Test
    public void precompiledHeader_dependsOnCompilerAndOptions() {
        java.util.List<String> uno = ArduinoAvrDirectBuild.compileOptions("/r", "standard", "atmega328p", "ARDUINO_AVR_UNO", "avr");
        java.util.List<String> mega = ArduinoAvrDirectBuild.compileOptions("/r", "mega", "atmega2560", "ARDUINO_AVR_MEGA2560", "avr");
        Assert.assertEquals(ArduinoAvrDirectBuild.fingerprint("avr-g++", uno), ArduinoAvrDirectBuild.fingerprint("avr-g++", uno));
        Assert.assertNotEquals(ArduinoAvrDirectBuild.fingerprint("avr-g++", uno), ArduinoAvrDirectBuild.fingerprint("avr-g++", mega));
        Assert.assertNotEquals(ArduinoAvrDirectBuild.fingerprint("avr-g++", uno), ArduinoAvrDirectBuild.fingerprint("/other/avr-g++", uno));
        Assert.assertTrue(uno.contains("-mmcu=atmega328p"));
        Assert.assertFalse(uno.contains("-c"));
    }
}
