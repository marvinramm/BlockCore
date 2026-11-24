#ifndef OPEN_ROBERTA_L293D_MOTOR_H
#define OPEN_ROBERTA_L293D_MOTOR_H

#include <Arduino.h>

class L293DMotor {
  public:
    enum Port : uint8_t {
        M1 = 0,
        M2 = 1,
        M3 = 2,
        M4 = 3
    };

    constexpr explicit L293DMotor(Port port)
        : port(port) {
    }

    static void setup() {
        pinMode(OUTPUT_ENABLE_PIN, OUTPUT);
        digitalWrite(OUTPUT_ENABLE_PIN, HIGH);
        pinMode(LATCH_PIN, OUTPUT);
        pinMode(CLOCK_PIN, OUTPUT);
        pinMode(DATA_PIN, OUTPUT);

        for ( uint8_t i = 0; i < 4; i++ ) {
            pinMode(pwmPins()[i], OUTPUT);
            analogWrite(pwmPins()[i], 0);
        }

        latchState() = 0;
        writeLatch();
        for ( uint8_t i = 0; i < 4; i++ ) {
            lastSpeed()[i] = 0;
        }
        digitalWrite(OUTPUT_ENABLE_PIN, LOW);
    }

    /**
     * Sets the speed in percent (-100 .. 100, negative = backwards, 0 = off). Calling it again with the same speed changes nothing, so it
     * may be called in every pass of a loop: the motor is only switched off briefly when its direction changes.
     */
    void run(int speed) const {
        speed = constrain(speed, -100, 100);
        int8_t& last = lastSpeed()[this->port];
        if ( speed == last ) {
            return;
        }
        uint8_t pwm = static_cast<uint8_t>(abs(speed) * 255L / 100);
        bool sameDirection = (speed > 0 && last > 0) || (speed < 0 && last < 0);
        last = static_cast<int8_t>(speed);
        if ( sameDirection ) {
            analogWrite(pwmPins()[this->port], pwm);
            return;
        }
        analogWrite(pwmPins()[this->port], 0);
        uint8_t directionMask = (1U << directionA()[this->port]) | (1U << directionB()[this->port]);
        latchState() &= static_cast<uint8_t>(~directionMask);
        if ( speed != 0 ) {
            latchState() |= 1U << (speed > 0 ? directionA()[this->port] : directionB()[this->port]);
        }
        writeLatch();
        if ( speed != 0 ) {
            analogWrite(pwmPins()[this->port], pwm);
        }
    }

  private:
    static const uint8_t OUTPUT_ENABLE_PIN = 7;
    static const uint8_t LATCH_PIN = 12;
    static const uint8_t CLOCK_PIN = 4;
    static const uint8_t DATA_PIN = 8;

    const Port port;

    static const uint8_t* pwmPins() {
        static const uint8_t pins[4] = {11, 3, 6, 5};
        return pins;
    }

    static const uint8_t* directionA() {
        static const uint8_t bits[4] = {2, 1, 5, 0};
        return bits;
    }

    static const uint8_t* directionB() {
        static const uint8_t bits[4] = {3, 4, 7, 6};
        return bits;
    }

    /** speed set last for each port (0 after setup()) */
    static int8_t* lastSpeed() {
        static int8_t speeds[4] = {0, 0, 0, 0};
        return speeds;
    }

    static uint8_t& latchState() {
        static uint8_t state = 0;
        return state;
    }

    static void writeLatch() {
        digitalWrite(LATCH_PIN, LOW);
        shiftOut(DATA_PIN, CLOCK_PIN, MSBFIRST, latchState());
        digitalWrite(LATCH_PIN, HIGH);
    }
};

#endif
