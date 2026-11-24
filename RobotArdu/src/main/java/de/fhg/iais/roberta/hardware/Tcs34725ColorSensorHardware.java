package de.fhg.iais.roberta.hardware;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;

/**
 * Hardware description for the TCS34725 colour sensor.
 *
 * The sensor is connected via I2C. On the Arduino Uno and Nano the I2C pins are A4 (SDA) and A5 (SCL), so these pins must not be used by
 * other configuration components. The thresholds for black and white are given in percent of the maximal brightness and can be changed in
 * the configuration block.
 */
public final class Tcs34725ColorSensorHardware {
    public static final String BLACK_PROPERTY = "TCS34725_BLACK";
    public static final String WHITE_PROPERTY = "TCS34725_WHITE";
    public static final String DEFAULT_BLACK_PERCENT = "2";
    public static final String DEFAULT_WHITE_PERCENT = "12";

    public static final String RED = "RGB(0xCC, 0x00, 0x00)";
    public static final String GREEN = "RGB(0x33, 0xCC, 0x00)";
    public static final String WHITE = "RGB(0xFF, 0xFF, 0xFF)";
    public static final String BLACK = "RGB(0x00, 0x00, 0x00)";
    public static final String NONE = "RGB(0x99, 0x99, 0x99)";

    private static final Set<String> I2C_PINS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("A4", "A5")));

    private Tcs34725ColorSensorHardware() {
    }

    public static Set<String> getI2cPins() {
        return I2C_PINS;
    }

    public static String getBlackThreshold(ConfigurationComponent component) {
        return getThreshold(component, BLACK_PROPERTY, DEFAULT_BLACK_PERCENT);
    }

    public static String getWhiteThreshold(ConfigurationComponent component) {
        return getThreshold(component, WHITE_PROPERTY, DEFAULT_WHITE_PERCENT);
    }

    public static boolean isValidThreshold(String value) {
        return parse(value) != null;
    }

    private static String getThreshold(ConfigurationComponent component, String property, String defaultValue) {
        Double value = parse(component.getOptProperty(property));
        if ( value == null ) {
            return defaultValue;
        }
        return String.format(Locale.ROOT, "%s", value);
    }

    private static Double parse(String value) {
        if ( value == null ) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim().replace(',', '.'));
            if ( Double.isNaN(parsed) || parsed < 0 || parsed > 100 ) {
                return null;
            }
            return parsed;
        } catch ( NumberFormatException e ) {
            return null;
        }
    }
}
