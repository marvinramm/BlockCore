package de.fhg.iais.roberta.syntax.configuration.sensor;

import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;
import de.fhg.iais.roberta.transformer.forClass.NepoConfiguration;
import de.fhg.iais.roberta.util.dbc.DbcException;

/** Analog joystick module (KY-023): two potentiometers and a push button. */
@NepoConfiguration(name = "ANALOGJOYSTICK", category = "CONFIGURATION_SENSOR", blocklyNames = {"robConf_analogjoystick"})
public final class AnalogJoystickSensor extends ConfigurationComponent {
    private AnalogJoystickSensor() {
        super(null, null, null, null, null);
        throw new DbcException("should NEVER be called");
    }
}
