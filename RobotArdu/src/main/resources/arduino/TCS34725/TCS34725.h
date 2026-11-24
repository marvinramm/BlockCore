#ifndef OPEN_ROBERTA_TCS34725_H
#define OPEN_ROBERTA_TCS34725_H

#include <Arduino.h>
#include <Wire.h>

/**
 * Minimal driver for the TCS34725 colour sensor (I2C address 0x29, SDA = A4, SCL = A5 on the Uno).
 *
 * The sensor runs with 50 ms integration time and 4x gain. colour() classifies the reading as
 * red, green, white or black; every other reading is reported as "none".
 */
class TCS34725 {
  public:
    TCS34725(float blackMaxPercent, float whiteMinPercent)
        : blackMaxPercent(blackMaxPercent), whiteMinPercent(whiteMinPercent), found(false), c(0), r(0), g(0), b(0) {
    }

    void begin() {
        Wire.begin();
        write8(REG_ATIME, ATIME_50MS);
        write8(REG_CONTROL, GAIN_4X);
        write8(REG_ENABLE, ENABLE_PON);
        delay(3);
        write8(REG_ENABLE, ENABLE_PON | ENABLE_AEN);
        delay(INTEGRATION_MS + 10);
    }

    /**
     * Returns one of the given colour values: red, green, white, black or none (not recognised).
     */
    template <typename T>
    T colour(T red, T green, T white, T black, T none) {
        read();
        if ( !this->found ) {
            return none;
        }
        if ( this->c == 0 || light() < this->blackMaxPercent ) {
            return black;
        }
        if ( this->r > RED_FACTOR * this->g && this->r > RED_FACTOR * this->b ) {
            return red;
        }
        if ( this->g > GREEN_FACTOR * this->r && this->g > GREEN_FACTOR * this->b ) {
            return green;
        }
        if ( light() > this->whiteMinPercent ) {
            return white;
        }
        return none;
    }

    /** Brightness (clear channel) in percent of the maximum value. */
    double lightPercent() {
        read();
        return light();
    }

    /** Share of the red channel of the overall light in percent. */
    double redPercent() {
        read();
        return channelPercent(this->r);
    }

    /** Share of the green channel of the overall light in percent. */
    double greenPercent() {
        read();
        return channelPercent(this->g);
    }

    /** Share of the blue channel of the overall light in percent. */
    double bluePercent() {
        read();
        return channelPercent(this->b);
    }

  private:
    static const uint8_t ADDRESS = 0x29;
    static const uint8_t COMMAND_BIT = 0x80;
    static const uint8_t AUTO_INCREMENT = 0x20;
    static const uint8_t REG_ENABLE = 0x00;
    static const uint8_t REG_ATIME = 0x01;
    static const uint8_t REG_CONTROL = 0x0F;
    static const uint8_t REG_CDATAL = 0x14;
    static const uint8_t ENABLE_PON = 0x01;
    static const uint8_t ENABLE_AEN = 0x02;
    static const uint8_t ATIME_50MS = 0xEB;
    static const uint8_t GAIN_4X = 0x01;
    static const uint16_t INTEGRATION_MS = 50;
    // (256 - ATIME) * 1024 is the largest value the clear channel can reach.
    static constexpr double MAX_COUNT = (256 - ATIME_50MS) * 1024.0;
    static constexpr double RED_FACTOR = 1.5;
    static constexpr double GREEN_FACTOR = 1.2;

    const float blackMaxPercent;
    const float whiteMinPercent;
    bool found;
    uint16_t c;
    uint16_t r;
    uint16_t g;
    uint16_t b;

    void read() {
        Wire.beginTransmission(ADDRESS);
        Wire.write(COMMAND_BIT | AUTO_INCREMENT | REG_CDATAL);
        if ( Wire.endTransmission() != 0 ) {
            this->found = false;
            this->c = this->r = this->g = this->b = 0;
            return;
        }
        if ( Wire.requestFrom(ADDRESS, (uint8_t) 8) != 8 ) {
            this->found = false;
            this->c = this->r = this->g = this->b = 0;
            return;
        }
        this->found = true;
        this->c = read16FromWire();
        this->r = read16FromWire();
        this->g = read16FromWire();
        this->b = read16FromWire();
    }

    double light() const {
        double percent = this->c * 100.0 / MAX_COUNT;
        return percent > 100.0 ? 100.0 : percent;
    }

    double channelPercent(uint16_t channel) const {
        if ( this->c == 0 ) {
            return 0.0;
        }
        double percent = channel * 100.0 / this->c;
        return percent > 100.0 ? 100.0 : percent;
    }

    static uint16_t read16FromWire() {
        uint16_t low = Wire.read();
        uint16_t high = Wire.read();
        return (high << 8) | low;
    }

    static void write8(uint8_t reg, uint8_t value) {
        Wire.beginTransmission(ADDRESS);
        Wire.write(COMMAND_BIT | reg);
        Wire.write(value);
        Wire.endTransmission();
    }
};

#endif
