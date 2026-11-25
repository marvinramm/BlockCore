package de.fhg.iais.roberta.worker.validate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.fhg.iais.roberta.components.Project;
import de.fhg.iais.roberta.hardware.L293DMotorShield;
import de.fhg.iais.roberta.hardware.Tcs34725ColorSensorHardware;
import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;
import de.fhg.iais.roberta.typecheck.NepoInfo;
import de.fhg.iais.roberta.util.Key;
import de.fhg.iais.roberta.util.ast.BlocklyProperties;
import de.fhg.iais.roberta.util.syntax.SC;

public class ArduinoConfigurationValidator {
    private static final Set<String> OVERLAPPING_PINS = Stream.of("INPUT", "OUTPUT", "+", "S", "PULSE", "TRIG", "ECHO", "RED", "GREEN", "BLUE", "RST", "IN", "SDA", "RS", "E", "D4", "D5", "D6", "D7", "PIN1", "IN1", "IN2", "IN3", "IN4", "OUT", "VRX", "VRY", "SW").collect(Collectors.toCollection(HashSet::new));

    /** sensors on the I2C bus (SDA / SCL): they share the bus, each has a fixed address, so each type can be configured once */
    private static final Set<String> I2C_SENSORS = Stream.of(SC.TCS34725, SC.MPU6050).collect(Collectors.toCollection(HashSet::new));

    private final Project project;

    public ArduinoConfigurationValidator(Project project) {
        this.project = project;
    }

    public void validateConfiguration(List<String> freePins) {
        List<String> currentFreePins = new ArrayList<>(freePins);
        Collection<ConfigurationComponent> configurationComponents = project.getConfigurationAst().getConfigurationComponents().values();
        configurationComponents.stream().filter(component -> !component.componentType.equals(SC.L293D_MOTOR) && !I2C_SENSORS.contains(component.componentType))
            .forEach(component -> checkPinOverlap(component, currentFreePins, freePins));
        checkL293DMotorShield(configurationComponents, currentFreePins, freePins);
        checkTcs34725Thresholds(configurationComponents);
        checkI2cSensors(configurationComponents, currentFreePins);
        project.getConfigurationAst().getConfigurationComponents().forEach((k, v) -> checkRgbInternalUse(v));
        if ( project.getRobot().equals("nano") || project.getRobot().equals("uno") ) {
            checkTimerOverlap(project.getConfigurationAst().getConfigurationComponents().values());
        }
    }

    private void checkL293DMotorShield(Collection<ConfigurationComponent> configurationComponents, List<String> currentFreePins, List<String> availablePins) {
        List<ConfigurationComponent> motors = configurationComponents.stream()
            .filter(component -> component.componentType.equals(SC.L293D_MOTOR))
            .collect(Collectors.toList());
        if ( motors.isEmpty() ) {
            return;
        }

        Map<String, List<ConfigurationComponent>> motorsByPort = new HashMap<>();
        for ( ConfigurationComponent motor : motors ) {
            String port = motor.getOptProperty("PORT");
            if ( !L293DMotorShield.getPort(port).isPresent() ) {
                addConfigurationError(motor, "CONFIGURATION_ERROR_MISSING_PIN");
            } else {
                motorsByPort.computeIfAbsent(port, key -> new ArrayList<>()).add(motor);
            }
        }
        motorsByPort.values().stream().filter(components -> components.size() > 1).flatMap(Collection::stream)
            .forEach(component -> addConfigurationError(component, "CONFIGURATION_ERROR_OVERLAPPING_PORTS"));

        Set<String> shieldPins = L293DMotorShield.getOccupiedPins();
        boolean missingPin = shieldPins.stream().anyMatch(pin -> !availablePins.contains(pin));
        boolean overlappingPin = shieldPins.stream().anyMatch(pin -> availablePins.contains(pin) && !currentFreePins.contains(pin));
        if ( missingPin || overlappingPin ) {
            String error = missingPin ? "CONFIGURATION_ERROR_MISSING_PIN" : "CONFIGURATION_ERROR_OVERLAPPING_PORTS";
            motors.forEach(component -> addConfigurationError(component, error));
        }
        currentFreePins.removeAll(shieldPins);
    }

    private void checkTcs34725Thresholds(Collection<ConfigurationComponent> configurationComponents) {
        configurationComponents.stream().filter(component -> component.componentType.equals(SC.TCS34725)).forEach(sensor -> {
            String black = sensor.getOptProperty(Tcs34725ColorSensorHardware.BLACK_PROPERTY);
            String white = sensor.getOptProperty(Tcs34725ColorSensorHardware.WHITE_PROPERTY);
            if ( (black != null && !Tcs34725ColorSensorHardware.isValidThreshold(black))
                || (white != null && !Tcs34725ColorSensorHardware.isValidThreshold(white)) ) {
                addConfigurationError(sensor, "CONFIGURATION_ERROR_TCS34725_THRESHOLD");
            }
        });
    }

    /**
     * I2C sensors (TCS34725, MPU6050) use the I2C pins (A4/A5 on Uno and Nano). Other components must not use these pins as normal pins.
     * Each sensor type has a fixed I2C address, so it can be configured only once; different types share the bus.
     */
    private void checkI2cSensors(Collection<ConfigurationComponent> configurationComponents, List<String> currentFreePins) {
        List<ConfigurationComponent> sensors = configurationComponents.stream()
            .filter(component -> I2C_SENSORS.contains(component.componentType))
            .collect(Collectors.toList());
        if ( sensors.isEmpty() ) {
            return;
        }
        sensors.stream().collect(Collectors.groupingBy(component -> component.componentType)).values().stream()
            .filter(sameType -> sameType.size() > 1).flatMap(Collection::stream)
            .forEach(component -> addConfigurationError(component, "CONFIGURATION_ERROR_OVERLAPPING_PORTS"));
        Set<String> i2cPins = Tcs34725ColorSensorHardware.getI2cPins();
        boolean overlappingPin = configurationComponents.stream()
            .filter(component -> !I2C_SENSORS.contains(component.componentType))
            .anyMatch(component -> usesPinsAsNormalPins(component, i2cPins));
        if ( overlappingPin ) {
            sensors.forEach(component -> addConfigurationError(component, "CONFIGURATION_ERROR_OVERLAPPING_PORTS"));
        }
        currentFreePins.removeAll(i2cPins);
    }

    /**
     * @return true, if the component uses one of the given pins as a normal (non I2C) pin. Other I2C components (e.g. LCD 1602 I2C) may share the bus.
     */
    private static boolean usesPinsAsNormalPins(ConfigurationComponent component, Set<String> pins) {
        Map<String, String> properties = component.getComponentProperties();
        boolean isI2cComponent = properties.containsKey("SCL");
        return properties.entrySet().stream()
            .filter(property -> OVERLAPPING_PINS.contains(property.getKey()))
            .filter(property -> !(isI2cComponent && property.getKey().equals("SDA")))
            .anyMatch(property -> pins.contains(property.getValue()));
    }

    private void addConfigurationError(ConfigurationComponent component, String errorKey) {
        project.addToErrorCounter(1, null);
        project.setResult(Key.PROGRAM_INVALID_STATEMETNS);
        project.addToConfAnnotationList(component.getProperty().blocklyId, NepoInfo.error(errorKey));
    }

    private void checkTimerOverlap(Collection<ConfigurationComponent> configurationComponents) {
        List<ConfigurationComponent> buzzer = new ArrayList<ConfigurationComponent>();
        List<ConfigurationComponent> ir = new ArrayList<ConfigurationComponent>();
        List<ConfigurationComponent> servo = new ArrayList<ConfigurationComponent>();
        for ( ConfigurationComponent cc : configurationComponents ) {
            if ( cc.componentType.equals(SC.BUZZER) ) {
                buzzer.add(cc);
            } else if ( cc.componentType.equals(SC.SERVOMOTOR) ) {
                servo.add(cc);
            } else if ( cc.componentType.equals(SC.INFRARED) ) {
                ir.add(cc);
            }
        }
        if ( buzzer.size() > 0 && servo.size() > 0 && ir.size() > 0 ) {
            project.addToErrorCounter(1, null);
            project.setResult(Key.PROGRAM_INVALID_STATEMETNS);
            Stream.of(buzzer, servo, ir).flatMap(i -> i.stream()).map(cc -> cc.getProperty().blocklyId)
                .forEach(id -> project.addToConfAnnotationList(id, NepoInfo.error("CONFIGURATION_ERROR_TIMER_CONFLICT")));
        }
    }

    public void checkPinOverlap(ConfigurationComponent configurationComponent, List<String> currentFreePins, List<String> availablePins) {
        Map<String, String> componentProperties = configurationComponent.getComponentProperties();
        List<String> blockPins = new ArrayList<>();
        componentProperties.forEach((k, v) -> {
            if ( OVERLAPPING_PINS.contains(k) ) {
                if ( currentFreePins.contains(v) ) {
                    blockPins.add(v);
                    currentFreePins.removeIf(s -> s.equals(v) && !v.equals(SC.LED_BUILTIN)); // built in LED cannot overlap
                } else {
                    project.addToErrorCounter(1, null);
                    project.setResult(Key.PROGRAM_INVALID_STATEMETNS);
                    BlocklyProperties blocklyProperties = configurationComponent.getProperty();
                    String blockId = blocklyProperties.blocklyId;
                    if ( !availablePins.contains(v) ) {
                        project.addToConfAnnotationList(blockId, NepoInfo.error("CONFIGURATION_ERROR_MISSING_PIN"));
                    } else {
                        project.addToConfAnnotationList(blockId, NepoInfo.error("CONFIGURATION_ERROR_OVERLAPPING_PORTS"));
                    }
                }
            }
        });
        if ( blockPins.stream().distinct().count() != blockPins.size() ) {
            project.addToErrorCounter(1, null);
            project.setResult(Key.PROGRAM_INVALID_STATEMETNS);
            BlocklyProperties blocklyProperties = configurationComponent.getProperty();
            String blockId = blocklyProperties.blocklyId;
            project.addToConfAnnotationList(blockId, NepoInfo.error("CONFIGURATION_ERROR_OVERLAPPING_PORTS"));
        }
    }

    // TODO restructure validator worker for more generic usage patterns
    public void checkRgbInternalUse(ConfigurationComponent configurationComponent) {
        if ( configurationComponent.componentType.equals(SC.RGBLED) ) {
            Map<String, String> componentProperties = configurationComponent.getComponentProperties();
            componentProperties.forEach((k, v) -> {
                if ( v.equals(SC.LED_BUILTIN) && !project.getRobot().equals("unowifirev2") ) {
                    project.addToErrorCounter(1, null);
                    project.setResult(Key.PROGRAM_INVALID_STATEMETNS);
                    BlocklyProperties blocklyProperties = configurationComponent.getProperty();
                    project.addToConfAnnotationList(blocklyProperties.blocklyId, NepoInfo.error("CONFIGURATION_ERROR_NO_BUILTIN_RGBLED"));
                }
            });
        }
    }
}
