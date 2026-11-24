package de.fhg.iais.roberta.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

public class L293DMotorShieldTest {
    @Test
    public void motorPortsMapToTheirPwmPins() {
        assertEquals("11", L293DMotorShield.getPort("M1").get().getPwmPin());
        assertEquals("3", L293DMotorShield.getPort("M2").get().getPwmPin());
        assertEquals("6", L293DMotorShield.getPort("M3").get().getPwmPin());
        assertEquals("5", L293DMotorShield.getPort("M4").get().getPwmPin());
        assertFalse(L293DMotorShield.getPort("M5").isPresent());
    }

    @Test
    public void completeShieldPinFootprintIsReserved() {
        assertEquals(
            new HashSet<>(Arrays.asList("3", "4", "5", "6", "7", "8", "11", "12")),
            L293DMotorShield.getOccupiedPins());
        assertTrue(L293DMotorShield.getOccupiedPins().contains(L293DMotorShield.getPort("M1").get().getPwmPin()));
    }
}
