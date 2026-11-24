#ifndef OPEN_ROBERTA_TCS34725_H
#define OPEN_ROBERTA_TCS34725_H

#include <Arduino.h>
#include <Wire.h>

/**
 * Minimal driver for the TCS34725 colour sensor (I2C address 0x29, SDA = A4, SCL = A5 on the Uno).
 *
 * The sensor measures for 154 ms and adjusts its gain (1x, 4x, 16x, 60x) itself, so that dark and bright surfaces are both measured with
 * a good resolution. colour() classifies the reading as red, green, white or black; every other reading is reported as "none".
 *
 * lightPercent() is independent of the gain: 100 % is the largest value the clear channel reaches with 4x gain and 50 ms integration time
 * (the scale of the first version of this driver), so the thresholds for black and white keep their meaning.
 */
class TCS34725 {
  public:
    TCS34725(float blackMaxPercent, float whiteMinPercent)
        : blackMaxPercent(blackMaxPercent), whiteMinPercent(whiteMinPercent), found(false), gainIndex(2), c(0), r(0), g(0), b(0) {
    }

    void begin() {
        Wire.begin();
        write8(REG_ATIME, ATIME_154MS);
        write8(REG_CONTROL, GAIN_SETTINGS[this->gainIndex]);
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

    /** Brightness (clear channel) in percent, see the class comment for the scale. */
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
    // 64 integration cycles of 2.4 ms: 154 ms, the clear channel reaches 65535
    static const uint8_t ATIME_154MS = 0xC0;
    static const uint16_t INTEGRATION_MS = 154;
    static constexpr double MAX_COUNT = 65535.0;
    // reference of lightPercent(): maximum of the first driver version (4x gain, 50 ms = 21504 counts)
    static constexpr double REFERENCE_COUNT = 21504.0;
    static constexpr double REFERENCE_GAIN = 4.0;
    static constexpr double REFERENCE_MS = 50.0;
    // the gain is changed when the clear channel leaves this range
    static constexpr double LOW_LIMIT = 0.08 * 65535.0;
    static constexpr double HIGH_LIMIT = 0.80 * 65535.0;
    static constexpr double RED_FACTOR = 1.5;
    static constexpr double GREEN_FACTOR = 1.2;
    static const uint8_t GAIN_SETTINGS[4];
    static const uint8_t GAIN_FACTORS[4];

    const float blackMaxPercent;
    const float whiteMinPercent;
    bool found;
    uint8_t gainIndex;
    uint16_t c;
    uint16_t r;
    uint16_t g;
    uint16_t b;

    /** reads the last measurement; if it is too dark or too bright, the gain is changed and measured again (at most twice) */
    void read() {
        readRaw();
        for ( uint8_t step = 0; step < 2 && this->found; step++ ) {
            if ( this->c < LOW_LIMIT && this->gainIndex < 3 ) {
                this->gainIndex++;
            } else if ( this->c > HIGH_LIMIT && this->gainIndex > 0 ) {
                this->gainIndex--;
            } else {
                return;
            }
            write8(REG_CONTROL, GAIN_SETTINGS[this->gainIndex]);
            delay(INTEGRATION_MS + 10);
            readRaw();
        }
    }

    void readRaw() {
        Wire.beginTransmission(ADDRESS);
        Wire.write(COMMAND_BIT | AUTO_INCREMENT | REG_CDATAL);
        if ( Wire.endTransmission() != 0 || Wire.requestFrom(ADDRESS, (uint8_t) 8) != 8 ) {
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
        double reference = this->c * (REFERENCE_GAIN / GAIN_FACTORS[this->gainIndex]) * (REFERENCE_MS / INTEGRATION_MS);
        double percent = reference * 100.0 / REFERENCE_COUNT;
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

const uint8_t TCS34725::GAIN_SETTINGS[4] = {0x00, 0x01, 0x02, 0x03};
const uint8_t TCS34725::GAIN_FACTORS[4] = {1, 4, 16, 60};

#endif
