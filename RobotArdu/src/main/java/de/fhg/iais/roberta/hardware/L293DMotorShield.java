package de.fhg.iais.roberta.hardware;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Hardware description for the L293D motor shield.
 *
 * The shield uses four PWM pins for its motor outputs and four shared control
 * pins for the shift register. Adding any motor therefore reserves the complete
 * shield pin footprint.
 */
public final class L293DMotorShield {
    public enum Port {
        M1("11"),
        M2("3"),
        M3("6"),
        M4("5");

        private final String pwmPin;

        Port(String pwmPin) {
            this.pwmPin = pwmPin;
        }

        public String getPwmPin() {
            return this.pwmPin;
        }
    }

    private static final Set<String> CONTROL_PINS =
        Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("4", "7", "8", "12")));
    private static final Set<String> OCCUPIED_PINS = createOccupiedPins();

    private L293DMotorShield() {
    }

    public static Optional<Port> getPort(String portName) {
        return Arrays.stream(Port.values()).filter(port -> port.name().equals(portName)).findFirst();
    }

    public static Set<String> getOccupiedPins() {
        return OCCUPIED_PINS;
    }

    private static Set<String> createOccupiedPins() {
        Set<String> occupiedPins = new LinkedHashSet<>(CONTROL_PINS);
        Arrays.stream(Port.values()).map(Port::getPwmPin).forEach(occupiedPins::add);
        return Collections.unmodifiableSet(occupiedPins);
    }
}
