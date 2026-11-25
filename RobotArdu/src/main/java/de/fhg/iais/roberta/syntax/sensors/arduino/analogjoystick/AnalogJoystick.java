package de.fhg.iais.roberta.syntax.sensors.arduino.analogjoystick;

import de.fhg.iais.roberta.syntax.sensor.ExternalSensor;
import de.fhg.iais.roberta.transformer.forClass.F2M;
import de.fhg.iais.roberta.transformer.forClass.NepoExpr;
import de.fhg.iais.roberta.util.ast.BlocklyProperties;
import de.fhg.iais.roberta.util.ast.ExternalSensorBean;

/**
 * Analog joystick: X and Y position in percent (-100 .. 100) and the push button.
 */
@NepoExpr(name = "ANALOGJOYSTICK_SENSING", category = "SENSOR", blocklyNames = {"robSensors_analogjoystick_getSample"},
    sampleValues = {@F2M(field = "ANALOGJOYSTICK_X", mode = "X"), @F2M(field = "ANALOGJOYSTICK_Y", mode = "Y"), @F2M(field = "ANALOGJOYSTICK_PRESSED", mode = "PRESSED")})
public final class AnalogJoystick extends ExternalSensor {

    public AnalogJoystick(BlocklyProperties properties, ExternalSensorBean externalSensorBean) {
        super(properties, externalSensorBean);
        setReadOnly();
    }
}
