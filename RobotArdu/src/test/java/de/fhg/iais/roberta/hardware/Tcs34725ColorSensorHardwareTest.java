package de.fhg.iais.roberta.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;

import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;
import de.fhg.iais.roberta.util.syntax.SC;

public class Tcs34725ColorSensorHardwareTest {
    @Test
    public void i2cPinsAreReserved() {
        assertEquals(new HashSet<>(Arrays.asList("A4", "A5")), Tcs34725ColorSensorHardware.getI2cPins());
    }

    @Test
    public void configuredThresholdsAreUsed() {
        ConfigurationComponent sensor = sensorWithThresholds("3", "15.5");
        assertEquals("3.0", Tcs34725ColorSensorHardware.getBlackThreshold(sensor));
        assertEquals("15.5", Tcs34725ColorSensorHardware.getWhiteThreshold(sensor));
    }

    @Test
    public void commaIsAcceptedAsDecimalSeparator() {
        ConfigurationComponent sensor = sensorWithThresholds("1,5", "12");
        assertEquals("1.5", Tcs34725ColorSensorHardware.getBlackThreshold(sensor));
    }

    @Test
    public void invalidOrMissingThresholdsFallBackToDefaults() {
        ConfigurationComponent sensor = sensorWithThresholds("abc", "200");
        assertEquals(Tcs34725ColorSensorHardware.DEFAULT_BLACK_PERCENT, Tcs34725ColorSensorHardware.getBlackThreshold(sensor));
        assertEquals(Tcs34725ColorSensorHardware.DEFAULT_WHITE_PERCENT, Tcs34725ColorSensorHardware.getWhiteThreshold(sensor));

        ConfigurationComponent sensorWithoutThresholds = new ConfigurationComponent(SC.TCS34725, "CONFIGURATION_SENSOR", "F", "F", new HashMap<>());
        assertEquals(Tcs34725ColorSensorHardware.DEFAULT_BLACK_PERCENT, Tcs34725ColorSensorHardware.getBlackThreshold(sensorWithoutThresholds));
    }

    @Test
    public void thresholdValidation() {
        assertTrue(Tcs34725ColorSensorHardware.isValidThreshold("0"));
        assertTrue(Tcs34725ColorSensorHardware.isValidThreshold("100"));
        assertTrue(Tcs34725ColorSensorHardware.isValidThreshold(" 2,5 "));
        assertFalse(Tcs34725ColorSensorHardware.isValidThreshold("-1"));
        assertFalse(Tcs34725ColorSensorHardware.isValidThreshold("101"));
        assertFalse(Tcs34725ColorSensorHardware.isValidThreshold("1); evil();"));
        assertFalse(Tcs34725ColorSensorHardware.isValidThreshold(null));
    }

    private static ConfigurationComponent sensorWithThresholds(String black, String white) {
        Map<String, String> properties = new HashMap<>();
        properties.put(Tcs34725ColorSensorHardware.BLACK_PROPERTY, black);
        properties.put(Tcs34725ColorSensorHardware.WHITE_PROPERTY, white);
        return new ConfigurationComponent(SC.TCS34725, "CONFIGURATION_SENSOR", "F", "F", properties);
    }
}
