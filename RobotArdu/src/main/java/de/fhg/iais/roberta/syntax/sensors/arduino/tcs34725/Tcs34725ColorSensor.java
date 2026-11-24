package de.fhg.iais.roberta.syntax.sensors.arduino.tcs34725;

import de.fhg.iais.roberta.syntax.sensor.ExternalSensor;
import de.fhg.iais.roberta.transformer.forClass.F2M;
import de.fhg.iais.roberta.transformer.forClass.NepoExpr;
import de.fhg.iais.roberta.util.ast.BlocklyProperties;
import de.fhg.iais.roberta.util.ast.ExternalSensorBean;

/**
 * TCS34725 colour sensor (I2C). Mode COLOUR classifies the reading as red, green, white or black.
 */
@NepoExpr(name = "TCS34725_SENSING", category = "SENSOR", blocklyNames = {"robSensors_tcs34725_getSample"},
    sampleValues = {@F2M(field = "TCS34725_COLOUR", mode = "COLOUR"), @F2M(field = "TCS34725_LIGHT", mode = "LIGHT"),
        @F2M(field = "TCS34725_REDCHANNEL", mode = "REDCHANNEL"), @F2M(field = "TCS34725_GREENCHANNEL", mode = "GREENCHANNEL"),
        @F2M(field = "TCS34725_BLUECHANNEL", mode = "BLUECHANNEL")})
public final class Tcs34725ColorSensor extends ExternalSensor {

    public Tcs34725ColorSensor(BlocklyProperties properties, ExternalSensorBean externalSensorBean) {
        super(properties, externalSensorBean);
        setReadOnly();
    }
}
