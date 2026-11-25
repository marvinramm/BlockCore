package de.fhg.iais.roberta.syntax.sensors.arduino.ds18b20;

import de.fhg.iais.roberta.syntax.sensor.ExternalSensor;
import de.fhg.iais.roberta.transformer.forClass.F2M;
import de.fhg.iais.roberta.transformer.forClass.NepoExpr;
import de.fhg.iais.roberta.util.ast.BlocklyProperties;
import de.fhg.iais.roberta.util.ast.ExternalSensorBean;

/**
 * DS18B20 temperature sensor in degrees Celsius.
 */
@NepoExpr(name = "DS18B20_SENSING", category = "SENSOR", blocklyNames = {"robSensors_ds18b20_getSample"},
    sampleValues = {@F2M(field = "DS18B20_TEMPERATURE", mode = "TEMPERATURE")})
public final class Ds18b20TemperatureSensor extends ExternalSensor {

    public Ds18b20TemperatureSensor(BlocklyProperties properties, ExternalSensorBean externalSensorBean) {
        super(properties, externalSensorBean);
        setReadOnly();
    }
}
