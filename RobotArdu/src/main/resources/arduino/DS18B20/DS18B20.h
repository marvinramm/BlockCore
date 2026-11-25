#ifndef BLOCKCORE_DS18B20_H
#define BLOCKCORE_DS18B20_H

#include <Arduino.h>

/**
 * Minimal driver for one DS18B20 temperature sensor (1-Wire bus, e.g. the waterproof probe; a 4.7 kOhm pull-up resistor from data to
 * VCC is needed, most modules have it).
 *
 * A measurement takes 750 ms. temperature() never waits: it returns the last measurement and starts the next one in the background.
 * Without a sensor it returns -127.
 */
class DS18B20 {
  public:
    explicit DS18B20(uint8_t pin)
        : pin(pin), value(NO_SENSOR), converting(false), started(0) {
    }

    void begin() {
        pinMode(pin, INPUT);
        startConversion();
        delay(CONVERSION_MS);
        readScratchpad();
        startConversion();
    }

    /** temperature in degrees Celsius (-127: no sensor found) */
    double temperature() {
        if ( !converting || millis() - started >= CONVERSION_MS ) {
            if ( converting ) {
                readScratchpad();
            }
            startConversion();
        }
        return value;
    }

  private:
    static constexpr double NO_SENSOR = -127.0;
    static const unsigned long CONVERSION_MS = 750;
    static const uint8_t SKIP_ROM = 0xCC;
    static const uint8_t CONVERT_T = 0x44;
    static const uint8_t READ_SCRATCHPAD = 0xBE;

    const uint8_t pin;
    double value;
    bool converting;
    unsigned long started;

    void startConversion() {
        converting = false;
        if ( !reset() ) {
            value = NO_SENSOR;
            return;
        }
        writeByte(SKIP_ROM);
        writeByte(CONVERT_T);
        converting = true;
        started = millis();
    }

    void readScratchpad() {
        uint8_t data[9];
        if ( !reset() ) {
            value = NO_SENSOR;
            return;
        }
        writeByte(SKIP_ROM);
        writeByte(READ_SCRATCHPAD);
        for ( uint8_t i = 0; i < 9; i++ ) {
            data[i] = readByte();
        }
        if ( crc8(data, 8) != data[8] ) {
            return; // disturbed transfer: keep the last value
        }
        int16_t raw = (int16_t) ((data[1] << 8) | data[0]);
        value = raw / 16.0;
    }

    // ---- 1-Wire (standard speed). The line is pulled low by switching the pin to output (it is LOW), released by switching to input.
    void low() {
        digitalWrite(pin, LOW);
        pinMode(pin, OUTPUT);
    }

    void release() {
        pinMode(pin, INPUT);
    }

    bool reset() {
        low();
        delayMicroseconds(480);
        noInterrupts();
        release();
        delayMicroseconds(70);
        bool present = digitalRead(pin) == LOW;
        interrupts();
        delayMicroseconds(410);
        return present;
    }

    void writeBit(bool bit) {
        noInterrupts();
        low();
        if ( bit ) {
            delayMicroseconds(6);
            release();
            interrupts();
            delayMicroseconds(64);
        } else {
            delayMicroseconds(60);
            release();
            interrupts();
            delayMicroseconds(10);
        }
    }

    bool readBit() {
        noInterrupts();
        low();
        delayMicroseconds(3);
        release();
        delayMicroseconds(10);
        bool bit = digitalRead(pin) == HIGH;
        interrupts();
        delayMicroseconds(53);
        return bit;
    }

    void writeByte(uint8_t data) {
        for ( uint8_t i = 0; i < 8; i++ ) {
            writeBit(data & 0x01);
            data >>= 1;
        }
    }

    uint8_t readByte() {
        uint8_t data = 0;
        for ( uint8_t i = 0; i < 8; i++ ) {
            if ( readBit() ) {
                data |= (uint8_t) (1 << i);
            }
        }
        return data;
    }

    static uint8_t crc8(const uint8_t *data, uint8_t length) {
        uint8_t crc = 0;
        while ( length-- ) {
            uint8_t inbyte = *data++;
            for ( uint8_t i = 8; i; i-- ) {
                uint8_t mix = (crc ^ inbyte) & 0x01;
                crc >>= 1;
                if ( mix ) {
                    crc ^= 0x8C;
                }
                inbyte >>= 1;
            }
        }
        return crc;
    }
};

#endif
