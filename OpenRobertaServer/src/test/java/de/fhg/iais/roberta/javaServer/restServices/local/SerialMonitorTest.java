package de.fhg.iais.roberta.javaServer.restServices.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class SerialMonitorTest {
    private SerialMonitor monitor;

    @Before
    public void setUp() {
        this.monitor = SerialMonitor.get();
        this.monitor.close();
        this.monitor.clear();
    }

    @Test
    public void readReturnsOnlyNewText() {
        long start = this.monitor.read(Long.MAX_VALUE).next;
        this.monitor.append("hello\n");
        SerialMonitor.Snapshot first = this.monitor.read(start);
        assertEquals("hello\n", first.data);
        this.monitor.append("world\n");
        SerialMonitor.Snapshot second = this.monitor.read(first.next);
        assertEquals("world\n", second.data);
        assertEquals("", this.monitor.read(second.next).data);
        assertEquals("closed", second.state);
    }

    @Test
    public void oldTextIsDroppedWhenTheBufferIsFull() {
        long start = this.monitor.read(Long.MAX_VALUE).next;
        StringBuilder big = new StringBuilder();
        for ( int i = 0; i < SerialMonitor.MAX_BUFFER + 10; i++ ) {
            big.append('x');
        }
        this.monitor.append(big.toString());
        this.monitor.append("end");
        SerialMonitor.Snapshot snapshot = this.monitor.read(start + 1);
        assertTrue(snapshot.truncated);
        assertEquals(SerialMonitor.MAX_BUFFER, snapshot.data.length());
        assertTrue(snapshot.data.endsWith("end"));
    }

    @Test
    public void clearKeepsPositionsGrowing() {
        this.monitor.append("abc");
        long before = this.monitor.read(Long.MAX_VALUE).next;
        this.monitor.clear();
        assertEquals(before, this.monitor.read(Long.MAX_VALUE).next);
        this.monitor.append("d");
        assertEquals("d", this.monitor.read(before).data);
        assertFalse(this.monitor.read(before).truncated);
    }

    @Test
    public void pauseWithoutOpenMonitorDoesNothing() {
        assertNull(this.monitor.pause("COM3"));
    }

    @Test(expected = IllegalStateException.class)
    public void writeNeedsAnOpenMonitor() {
        this.monitor.write("x");
    }

    @Test
    public void baudrates() {
        assertTrue(LocalSerialController.isValidBaudrate(9600));
        assertTrue(LocalSerialController.isValidBaudrate(115200));
        assertFalse(LocalSerialController.isValidBaudrate(1234));
    }
}
