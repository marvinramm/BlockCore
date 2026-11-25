package de.fhg.iais.roberta.javaServer.restServices.local;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LocalSerialControllerTest {
    private static final String HEX = ":100000000C9434000C9446000C9446000C9446006A\n:00000001FF\n";

    @Test
    public void extractHex_removesPrefix() {
        assertEquals(HEX, LocalSerialController.extractHex("null" + HEX));
        assertEquals(HEX, LocalSerialController.extractHex(HEX));
    }

    @Test(expected = IllegalArgumentException.class)
    public void extractHex_rejectsNonHex() {
        LocalSerialController.extractHex("no hex here");
    }

    @Test
    public void portArgument() {
        assertEquals("COM3", LocalSerialController.portArgument("COM3", true));
        assertEquals("\\\\.\\COM12", LocalSerialController.portArgument("COM12", true));
        assertEquals("/dev/ttyACM0", LocalSerialController.portArgument("ttyACM0", false));
        assertEquals("/dev/ttyUSB0", LocalSerialController.portArgument("/dev/ttyUSB0", false));
    }

    @Test
    public void avrdudeTargets() {
        assertEquals("atmega328p", LocalSerialController.AvrdudeTarget.forRobot("uno").part);
        assertArrayEquals(new int[] {115200, 57600}, LocalSerialController.AvrdudeTarget.forRobot("nano").baudrates);
        assertEquals("wiring", LocalSerialController.AvrdudeTarget.forRobot("mega").programmer);
        assertNull(LocalSerialController.AvrdudeTarget.forRobot("ev3dev"));
    }

    @Test
    public void baudratesToTry_startsWithTheOneThatWorkedLastTime() {
        LocalSerialController.AvrdudeTarget nano = LocalSerialController.AvrdudeTarget.forRobot("nano");
        LocalSerialController.AvrdudeTarget uno = LocalSerialController.AvrdudeTarget.forRobot("uno");
        assertArrayEquals(new int[] {115200, 57600}, LocalSerialController.baudratesToTry(nano, null));
        assertArrayEquals(new int[] {115200, 57600}, LocalSerialController.baudratesToTry(nano, 115200));
        assertArrayEquals(new int[] {57600, 115200}, LocalSerialController.baudratesToTry(nano, 57600));
        assertArrayEquals(new int[] {115200}, LocalSerialController.baudratesToTry(uno, 57600));
        assertArrayEquals(new int[] {115200, 57600}, LocalSerialController.baudratesToTry(nano, 9600));
    }
}
