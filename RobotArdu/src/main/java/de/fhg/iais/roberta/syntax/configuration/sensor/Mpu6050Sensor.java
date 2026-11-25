package de.fhg.iais.roberta.syntax.configuration.sensor;

import de.fhg.iais.roberta.syntax.configuration.ConfigurationComponent;
import de.fhg.iais.roberta.transformer.forClass.NepoConfiguration;
import de.fhg.iais.roberta.util.dbc.DbcException;

/** MPU6050 gyro and acceleration sensor (I2C). */
@NepoConfiguration(name = "MPU6050", category = "CONFIGURATION_SENSOR", blocklyNames = {"robConf_mpu6050"})
public final class Mpu6050Sensor extends ConfigurationComponent {
    private Mpu6050Sensor() {
        super(null, null, null, null, null);
        throw new DbcException("should NEVER be called");
    }
}
