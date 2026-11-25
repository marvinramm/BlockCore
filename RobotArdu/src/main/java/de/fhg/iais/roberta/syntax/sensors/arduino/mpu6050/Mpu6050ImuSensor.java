package de.fhg.iais.roberta.syntax.sensors.arduino.mpu6050;

import de.fhg.iais.roberta.syntax.sensor.ExternalSensor;
import de.fhg.iais.roberta.transformer.forClass.F2M;
import de.fhg.iais.roberta.transformer.forClass.NepoExpr;
import de.fhg.iais.roberta.util.ast.BlocklyProperties;
import de.fhg.iais.roberta.util.ast.ExternalSensorBean;

/**
 * MPU6050 gyro and acceleration sensor: tilt angles (roll, pitch), rotation speed, acceleration and temperature.
 */
@NepoExpr(name = "MPU6050_SENSING", category = "SENSOR", blocklyNames = {"robSensors_mpu6050_getSample"},
    sampleValues = {@F2M(field = "MPU6050_ROLL", mode = "ROLL"), @F2M(field = "MPU6050_PITCH", mode = "PITCH"), @F2M(field = "MPU6050_GYRO_X", mode = "GYRO_X"), @F2M(field = "MPU6050_GYRO_Y", mode = "GYRO_Y"), @F2M(field = "MPU6050_GYRO_Z", mode = "GYRO_Z"), @F2M(field = "MPU6050_ACCEL_X", mode = "ACCEL_X"), @F2M(field = "MPU6050_ACCEL_Y", mode = "ACCEL_Y"), @F2M(field = "MPU6050_ACCEL_Z", mode = "ACCEL_Z"), @F2M(field = "MPU6050_TEMPERATURE", mode = "TEMPERATURE")})
public final class Mpu6050ImuSensor extends ExternalSensor {

    public Mpu6050ImuSensor(BlocklyProperties properties, ExternalSensorBean externalSensorBean) {
        super(properties, externalSensorBean);
        setReadOnly();
    }
}
