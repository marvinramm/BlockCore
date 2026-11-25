#ifndef BLOCKCORE_MPU6050_H
#define BLOCKCORE_MPU6050_H

#include <Arduino.h>
#include <Wire.h>
#include <math.h>

/**
 * Minimal driver for the MPU6050 gyro and acceleration sensor (I2C address 0x68, or 0x69 if AD0 is high; SDA = A4, SCL = A5 on the Uno).
 *
 * roll() and pitch() are tilt angles in degrees. They come from a complementary filter: the gyro follows fast movements, the
 * acceleration sensor (which knows where "down" is) corrects the drift of the gyro. The gyro is calibrated in begin(): keep the sensor
 * still for half a second after the start.
 *
 * Reading never blocks: a new measurement is read at most every 2 ms, otherwise the last one is returned.
 */
class MPU6050 {
  public:
    MPU6050()
        : address(0x68), found(false), ax(0), ay(0), az(0), temp(0), gx(0), gy(0), gz(0), offsetX(0), offsetY(0), offsetZ(0), rollAngle(0),
          pitchAngle(0), lastUpdate(0) {
    }

    void begin() {
        Wire.begin();
        if ( !probe(0x68) ) {
            probe(0x69);
        }
        write8(REG_PWR_MGMT_1, 0x01); // wake up, clock from the X gyro
        write8(REG_CONFIG, 0x03); // low pass filter 44 Hz
        write8(REG_GYRO_CONFIG, 0x08); // +-500 degrees per second
        write8(REG_ACCEL_CONFIG, 0x00); // +-2 g
        delay(50);
        // gyro calibration: the sensor lies still
        long sumX = 0, sumY = 0, sumZ = 0;
        for ( int i = 0; i < CALIBRATION_SAMPLES; i++ ) {
            readRaw();
            sumX += gx;
            sumY += gy;
            sumZ += gz;
            delay(3);
        }
        offsetX = sumX / (float) CALIBRATION_SAMPLES;
        offsetY = sumY / (float) CALIBRATION_SAMPLES;
        offsetZ = sumZ / (float) CALIBRATION_SAMPLES;
        readRaw();
        rollAngle = accelRoll();
        pitchAngle = accelPitch();
        lastUpdate = micros();
    }

    /** tilt to the side (around the X axis) in degrees */
    double roll() {
        update();
        return rollAngle;
    }

    /** tilt forwards / backwards (around the Y axis) in degrees */
    double pitch() {
        update();
        return pitchAngle;
    }

    /** rotation speed in degrees per second */
    double gyroX() {
        update();
        return (gx - offsetX) / GYRO_LSB;
    }

    double gyroY() {
        update();
        return (gy - offsetY) / GYRO_LSB;
    }

    double gyroZ() {
        update();
        return (gz - offsetZ) / GYRO_LSB;
    }

    /** acceleration in g (1 g = gravity) */
    double accelX() {
        update();
        return ax / ACCEL_LSB;
    }

    double accelY() {
        update();
        return ay / ACCEL_LSB;
    }

    double accelZ() {
        update();
        return az / ACCEL_LSB;
    }

    /** temperature of the chip in degrees Celsius */
    double temperature() {
        update();
        return temp / 340.0 + 36.53;
    }

  private:
    static const uint8_t REG_CONFIG = 0x1A;
    static const uint8_t REG_GYRO_CONFIG = 0x1B;
    static const uint8_t REG_ACCEL_CONFIG = 0x1C;
    static const uint8_t REG_ACCEL_XOUT_H = 0x3B;
    static const uint8_t REG_PWR_MGMT_1 = 0x6B;
    static const int CALIBRATION_SAMPLES = 150;
    static constexpr float GYRO_LSB = 65.5f;
    static constexpr float ACCEL_LSB = 16384.0f;
    static constexpr float GYRO_WEIGHT = 0.98f;

    uint8_t address;
    bool found;
    int16_t ax, ay, az, temp, gx, gy, gz;
    float offsetX, offsetY, offsetZ;
    float rollAngle, pitchAngle;
    unsigned long lastUpdate;

    bool probe(uint8_t candidate) {
        Wire.beginTransmission(candidate);
        if ( Wire.endTransmission() == 0 ) {
            address = candidate;
            found = true;
        }
        return found;
    }

    float accelRoll() const {
        return atan2((float) ay, (float) az) * 180.0f / (float) PI;
    }

    float accelPitch() const {
        return atan2(-(float) ax, sqrt((float) ay * ay + (float) az * az)) * 180.0f / (float) PI;
    }

    void update() {
        unsigned long now = micros();
        unsigned long elapsed = now - lastUpdate;
        if ( elapsed < 2000UL ) {
            return;
        }
        if ( !readRaw() ) {
            return;
        }
        lastUpdate = now;
        float dt = elapsed / 1000000.0f;
        if ( dt > 0.5f ) {
            // not read for a long time: the gyro sum would be wrong, start again from the acceleration sensor
            rollAngle = accelRoll();
            pitchAngle = accelPitch();
            return;
        }
        rollAngle = GYRO_WEIGHT * (rollAngle + (gx - offsetX) / GYRO_LSB * dt) + (1.0f - GYRO_WEIGHT) * accelRoll();
        pitchAngle = GYRO_WEIGHT * (pitchAngle + (gy - offsetY) / GYRO_LSB * dt) + (1.0f - GYRO_WEIGHT) * accelPitch();
    }

    bool readRaw() {
        Wire.beginTransmission(address);
        Wire.write(REG_ACCEL_XOUT_H);
        if ( Wire.endTransmission(false) != 0 || Wire.requestFrom(address, (uint8_t) 14) != 14 ) {
            found = false;
            return false;
        }
        found = true;
        ax = read16();
        ay = read16();
        az = read16();
        temp = read16();
        gx = read16();
        gy = read16();
        gz = read16();
        return true;
    }

    static int16_t read16() {
        int16_t high = Wire.read();
        int16_t low = Wire.read();
        return (int16_t) ((high << 8) | low);
    }

    void write8(uint8_t reg, uint8_t value) {
        Wire.beginTransmission(address);
        Wire.write(reg);
        Wire.write(value);
        Wire.endTransmission();
    }
};

#endif
