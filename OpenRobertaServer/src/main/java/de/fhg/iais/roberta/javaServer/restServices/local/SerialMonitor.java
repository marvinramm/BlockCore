package de.fhg.iais.roberta.javaServer.restServices.local;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;

/**
 * The serial monitor of the local (standalone) mode: reads the text a robot prints (e.g. with "show on Serial Monitor") from a serial port and keeps
 * it in a buffer, from which the browser polls. Only one port can be monitored at a time.<br>
 * <br>
 * During an upload the port is needed by avrdude: {@link #pause(String)} closes the monitor, {@link #resume(Paused)} opens it again.
 */
final class SerialMonitor {
    private static final Logger LOG = LoggerFactory.getLogger(SerialMonitor.class);
    static final int MAX_BUFFER = 200_000;
    private static final SerialMonitor INSTANCE = new SerialMonitor();

    private final StringBuilder buffer = new StringBuilder();
    /** absolute position of the first character in {@link #buffer} */
    private long base = 0;
    private SerialPort port;
    private String portName;
    private int baudrate;
    private Thread reader;
    private String state = "closed";

    static SerialMonitor get() {
        return INSTANCE;
    }

    /** remembers a monitor that was closed for an upload */
    static final class Paused {
        final String portName;
        final int baudrate;

        Paused(String portName, int baudrate) {
            this.portName = portName;
            this.baudrate = baudrate;
        }
    }

    synchronized void open(SerialPort serialPort, String name, int baudrate) {
        closeInternal(null);
        serialPort.setComPortParameters(baudrate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
        if ( !serialPort.openPort() ) {
            this.state = "error";
            throw new IllegalStateException("port " + name + " cannot be opened. Is it used by another program?");
        }
        this.port = serialPort;
        this.portName = name;
        this.baudrate = baudrate;
        this.state = "open";
        final SerialPort readFrom = serialPort;
        this.reader = new Thread(() -> readLoop(readFrom), "serial-monitor-" + name);
        this.reader.setDaemon(true);
        this.reader.start();
        LOG.info("serial monitor opened on " + name + " with " + baudrate + " baud");
    }

    synchronized void close() {
        closeInternal(null);
    }

    /**
     * close the monitor, if it uses the given port, because avrdude needs the port.
     *
     * @return the information to reopen the monitor later, or null, if the monitor did not use the port
     */
    synchronized Paused pause(String name) {
        if ( this.port == null || !name.equals(this.portName) ) {
            return null;
        }
        Paused paused = new Paused(this.portName, this.baudrate);
        closeInternal("\n--- upload, monitor paused ---\n");
        return paused;
    }

    void resume(Paused paused, SerialPort serialPort) {
        if ( paused == null || serialPort == null ) {
            return;
        }
        try {
            Thread.sleep(300); // the board restarts after the upload
            open(serialPort, paused.portName, paused.baudrate);
            append("--- monitor continued ---\n");
        } catch ( Exception e ) {
            append("--- monitor could not be continued: " + e.getMessage() + " ---\n");
        }
    }

    synchronized void write(String text) {
        if ( this.port == null ) {
            throw new IllegalStateException("the serial monitor is not open");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        this.port.writeBytes(bytes, bytes.length);
    }

    synchronized void clear() {
        this.base += this.buffer.length();
        this.buffer.setLength(0);
    }

    /**
     * @param since absolute position of the first character the caller wants. Characters that were already dropped from the buffer are skipped.
     */
    synchronized Snapshot read(long since) {
        long end = this.base + this.buffer.length();
        long from = Math.max(since, this.base);
        String data = from >= end ? "" : this.buffer.substring((int) (from - this.base));
        return new Snapshot(data, end, this.state, this.portName, this.baudrate, since < this.base && since > 0);
    }

    static final class Snapshot {
        final String data;
        final long next;
        final String state;
        final String port;
        final int baudrate;
        final boolean truncated;

        Snapshot(String data, long next, String state, String port, int baudrate, boolean truncated) {
            this.data = data;
            this.next = next;
            this.state = state;
            this.port = port;
            this.baudrate = baudrate;
            this.truncated = truncated;
        }
    }

    synchronized void append(String text) {
        this.buffer.append(text);
        int overflow = this.buffer.length() - MAX_BUFFER;
        if ( overflow > 0 ) {
            this.buffer.delete(0, overflow);
            this.base += overflow;
        }
    }

    private void closeInternal(String marker) {
        SerialPort old = this.port;
        this.port = null;
        if ( this.reader != null ) {
            this.reader.interrupt();
            this.reader = null;
        }
        if ( old != null ) {
            old.closePort();
            LOG.info("serial monitor closed on " + this.portName);
        }
        if ( marker != null ) {
            append(marker);
        }
        this.state = "closed";
    }

    private void readLoop(SerialPort serialPort) {
        CharsetDecoder decoder =
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer bytes = ByteBuffer.allocate(4096);
        CharBuffer chars = CharBuffer.allocate(4096);
        byte[] chunk = new byte[4096];
        while ( !Thread.currentThread().isInterrupted() ) {
            int available = serialPort.bytesAvailable();
            if ( available < 0 ) {
                synchronized ( this ) {
                    if ( this.port == serialPort ) {
                        closeInternal("\n--- connection lost (cable unplugged?) ---\n");
                    }
                }
                return;
            }
            if ( available == 0 ) {
                try {
                    Thread.sleep(20);
                } catch ( InterruptedException e ) {
                    return;
                }
                continue;
            }
            int read = serialPort.readBytes(chunk, Math.min(available, Math.min(chunk.length, bytes.remaining())));
            if ( read <= 0 ) {
                continue;
            }
            bytes.put(chunk, 0, read);
            bytes.flip();
            decoder.decode(bytes, chars, false);
            bytes.compact(); // keeps an incomplete UTF-8 sequence for the next chunk
            chars.flip();
            String text = chars.toString();
            chars.clear();
            synchronized ( this ) {
                if ( this.port != serialPort ) {
                    return;
                }
                append(text);
            }
        }
    }
}
