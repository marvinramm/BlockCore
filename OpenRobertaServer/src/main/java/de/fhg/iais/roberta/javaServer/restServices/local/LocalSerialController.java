package de.fhg.iais.roberta.javaServer.restServices.local;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.google.inject.Inject;

import de.fhg.iais.roberta.util.ServerProperties;

/**
 * REST services for the local (standalone) mode: the lab runs on the same computer the robot is connected to. The browser lists the serial ports
 * of this computer and the server uploads the compiled program with avrdude directly to the selected port. No connector program and no external
 * server are needed.<br>
 * <br>
 * The services are only active, if the server property <code>server.local.serial</code> is <code>true</code>. Because they allow to write to
 * serial ports, requests are only accepted from the local computer and from pages served by this server.
 */
@javax.ws.rs.Path("/local")
public class LocalSerialController {
    private static final Logger LOG = LoggerFactory.getLogger(LocalSerialController.class);
    private static final Object UPLOAD_LOCK = new Object();
    private static final long UPLOAD_TIMEOUT_SECONDS = 90;

    private final ServerProperties serverProperties;

    @Inject
    public LocalSerialController(ServerProperties serverProperties) {
        this.serverProperties = serverProperties;
    }

    /**
     * @return true, if the local mode is enabled and programs for this robot are uploaded by the browser via {@link #upload}
     */
    public static boolean isUploadedLocally(ServerProperties serverProperties, String robot) {
        return serverProperties.getBooleanProperty("server.local.serial") && robot != null && AvrdudeTarget.forRobot(robot) != null;
    }

    /**
     * state of the update: may BlockCore install updates itself (installed, not unzipped), and the progress of a download
     */
    @GET
    @javax.ws.rs.Path("/update")
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateStatus(@Context HttpServletRequest request) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        JSONObject json = LocalUpdate.status(updateDir() != null);
        json.put("rc", "ok");
        return Response.ok(json.toString(), MediaType.APPLICATION_JSON).build();
    }

    /**
     * downloads the installer of the latest release; then the server stops and the launcher installs it (see {@link LocalUpdate})
     */
    @POST
    @javax.ws.rs.Path("/update")
    @Produces(MediaType.APPLICATION_JSON)
    public Response startUpdate(@Context HttpServletRequest request) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        Path updateDir = updateDir();
        Path staticResources = LocalUpdate.path(this.serverProperties.getStringProperty("server.staticresources.dir"));
        if ( updateDir == null || staticResources == null ) {
            return answer(Response.Status.CONFLICT, false, "this BlockCore cannot update itself");
        }
        LocalUpdate.start(updateDir, staticResources);
        JSONObject json = LocalUpdate.status(true);
        json.put("rc", "ok");
        return Response.ok(json.toString(), MediaType.APPLICATION_JSON).build();
    }

    private Path updateDir() {
        return LocalUpdate.path(this.serverProperties.getStringProperty("server.local.update.dir"));
    }

    @GET
    @javax.ws.rs.Path("/ports")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPorts(@Context HttpServletRequest request) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        JSONArray ports = new JSONArray();
        for ( SerialPort port : SerialPort.getCommPorts() ) {
            JSONObject json = new JSONObject();
            json.put("name", port.getSystemPortName());
            json.put("path", portPath(port));
            json.put("description", port.getPortDescription());
            json.put("vendorId", port.getVendorID() < 0 ? "" : String.format("0x%04X", port.getVendorID()));
            json.put("productId", port.getProductID() < 0 ? "" : String.format("0x%04X", port.getProductID()));
            ports.put(json);
        }
        return Response.ok(new JSONObject().put("ports", ports).toString()).build();
    }

    @POST
    @javax.ws.rs.Path("/upload")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(@Context HttpServletRequest request, String body) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        JSONObject json;
        String port;
        String robot;
        String hex;
        try {
            json = new JSONObject(body);
            port = json.getString("port");
            robot = json.getString("robot");
            hex = extractHex(new String(Base64.getDecoder().decode(json.getString("hex")), StandardCharsets.UTF_8));
        } catch ( Exception e ) {
            return answer(Response.Status.BAD_REQUEST, false, "invalid request: " + e.getMessage());
        }
        if ( !isKnownPort(port) ) {
            return answer(Response.Status.BAD_REQUEST, false, "unknown serial port: " + port);
        }
        AvrdudeTarget target = AvrdudeTarget.forRobot(robot);
        if ( target == null ) {
            return answer(Response.Status.BAD_REQUEST, false, "robot not supported in local mode: " + robot);
        }
        synchronized ( UPLOAD_LOCK ) {
            Path hexFile = null;
            // avrdude needs the port: a serial monitor on it is paused and continued after the upload
            SerialMonitor.Paused pausedMonitor = SerialMonitor.get().pause(port);
            try {
                hexFile = Files.createTempFile("ora-upload-", ".hex");
                Files.write(hexFile, hex.getBytes(StandardCharsets.US_ASCII));
                StringBuilder log = new StringBuilder();
                boolean ok = false;
                for ( int baudrate : target.baudrates ) {
                    ok = runAvrdude(target, baudrate, port, hexFile, log);
                    if ( ok ) {
                        break;
                    }
                }
                if ( ok ) {
                    LOG.info("program uploaded to " + port + " (" + robot + ")");
                    return answer(Response.Status.OK, true, "Program uploaded to " + port);
                }
                LOG.warn("upload to " + port + " failed:\n" + log);
                return answer(Response.Status.OK, false, "Upload to " + port + " failed. Is the right port selected and no other program (e.g. the Arduino IDE) using it?\n" + tail(log.toString(), 1500));
            } catch ( IOException | InterruptedException e ) {
                LOG.error("upload to " + port + " failed", e);
                return answer(Response.Status.INTERNAL_SERVER_ERROR, false, "upload failed: " + e.getMessage());
            } finally {
                if ( hexFile != null ) {
                    hexFile.toFile().delete();
                }
                SerialMonitor.get().resume(pausedMonitor, findPort(port));
            }
        }
    }

    @POST
    @javax.ws.rs.Path("/monitor/open")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response openMonitor(@Context HttpServletRequest request, String body) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        String port;
        int baudrate;
        try {
            JSONObject json = new JSONObject(body);
            port = json.getString("port");
            baudrate = json.optInt("baudrate", 9600);
        } catch ( Exception e ) {
            return answer(Response.Status.BAD_REQUEST, false, "invalid request: " + e.getMessage());
        }
        if ( !isValidBaudrate(baudrate) ) {
            return answer(Response.Status.BAD_REQUEST, false, "unsupported baud rate: " + baudrate);
        }
        SerialPort serialPort = findPort(port);
        if ( serialPort == null ) {
            return answer(Response.Status.BAD_REQUEST, false, "unknown serial port: " + port);
        }
        synchronized ( UPLOAD_LOCK ) {
            try {
                SerialMonitor.get().open(serialPort, port, baudrate);
                return answer(Response.Status.OK, true, "monitor opened on " + port);
            } catch ( IllegalStateException e ) {
                return answer(Response.Status.OK, false, e.getMessage());
            }
        }
    }

    @POST
    @javax.ws.rs.Path("/monitor/close")
    @Produces(MediaType.APPLICATION_JSON)
    public Response closeMonitor(@Context HttpServletRequest request) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        SerialMonitor.get().close();
        return answer(Response.Status.OK, true, "monitor closed");
    }

    @POST
    @javax.ws.rs.Path("/monitor/clear")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearMonitor(@Context HttpServletRequest request) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        SerialMonitor.get().clear();
        return answer(Response.Status.OK, true, "monitor cleared");
    }

    @POST
    @javax.ws.rs.Path("/monitor/write")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeMonitor(@Context HttpServletRequest request, String body) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        try {
            SerialMonitor.get().write(new JSONObject(body).getString("text"));
            return answer(Response.Status.OK, true, "sent");
        } catch ( IllegalStateException e ) {
            return answer(Response.Status.OK, false, e.getMessage());
        } catch ( Exception e ) {
            return answer(Response.Status.BAD_REQUEST, false, "invalid request: " + e.getMessage());
        }
    }

    @GET
    @javax.ws.rs.Path("/monitor/read")
    @Produces(MediaType.APPLICATION_JSON)
    public Response readMonitor(@Context HttpServletRequest request, @QueryParam("since") long since) {
        Response rejection = checkAccess(request);
        if ( rejection != null ) {
            return rejection;
        }
        SerialMonitor.Snapshot snapshot = SerialMonitor.get().read(since);
        JSONObject json = new JSONObject();
        json.put("data", snapshot.data);
        json.put("next", snapshot.next);
        json.put("state", snapshot.state);
        json.put("port", snapshot.port == null ? JSONObject.NULL : snapshot.port);
        json.put("baudrate", snapshot.baudrate);
        json.put("truncated", snapshot.truncated);
        return Response.ok(json.toString()).build();
    }

    static boolean isValidBaudrate(int baudrate) {
        switch ( baudrate ) {
            case 300:
            case 1200:
            case 2400:
            case 4800:
            case 9600:
            case 19200:
            case 38400:
            case 57600:
            case 74880:
            case 115200:
            case 230400:
            case 250000:
            case 500000:
            case 1000000:
            case 2000000:
                return true;
            default:
                return false;
        }
    }

    private static SerialPort findPort(String name) {
        for ( SerialPort port : SerialPort.getCommPorts() ) {
            if ( port.getSystemPortName().equals(name) || portPath(port).equals(name) ) {
                return port;
            }
        }
        return null;
    }

    private boolean runAvrdude(AvrdudeTarget target, int baudrate, String port, Path hexFile, StringBuilder log) throws IOException, InterruptedException {
        File avrdudeDir = avrdudeDir();
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        File avrdude = new File(avrdudeDir, "bin" + File.separator + (windows ? "avrdude.exe" : "avrdude"));
        File config = new File(avrdudeDir, "etc" + File.separator + "avrdude.conf");
        List<String> command = new ArrayList<>();
        command.add(avrdude.getAbsolutePath());
        if ( config.isFile() ) {
            command.add("-C" + config.getAbsolutePath());
        }
        command.add("-p" + target.part);
        command.add("-c" + target.programmer);
        command.add("-P" + portArgument(port, windows));
        command.add("-b" + baudrate);
        command.add("-D");
        command.add("-Uflash:w:" + hexFile.toAbsolutePath() + ":i");
        log.append("> ").append(String.join(" ", command)).append('\n');
        Path output = Files.createTempFile("ora-avrdude-", ".log");
        try {
            // the output goes to a file: a full pipe buffer would block avrdude
            Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            boolean finished = process.waitFor(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if ( !finished ) {
                process.destroyForcibly();
            }
            log.append(new String(Files.readAllBytes(output), StandardCharsets.UTF_8));
            if ( !finished ) {
                log.append("avrdude timed out\n");
                return false;
            }
            return process.exitValue() == 0;
        } finally {
            output.toFile().delete();
        }
    }

    private File avrdudeDir() {
        String configured = this.serverProperties.getStringProperty("server.local.avrdude.dir");
        if ( configured != null && !configured.trim().isEmpty() ) {
            return new File(configured.trim());
        }
        return new File(this.serverProperties.getCrosscompilerResourceDir(), "RobotArdu" + File.separator + "arduino-resources" + File.separator + "avrdude");
    }

    /**
     * Only requests from this computer and from pages of this server are accepted. This prevents other web sites from flashing the robot.
     */
    private Response checkAccess(HttpServletRequest request) {
        if ( !this.serverProperties.getBooleanProperty("server.local.serial") ) {
            return answer(Response.Status.NOT_FOUND, false, "local serial mode is not enabled");
        }
        if ( !isLoopback(request.getRemoteAddr()) ) {
            return answer(Response.Status.FORBIDDEN, false, "only local requests are allowed");
        }
        String origin = request.getHeader("Origin");
        if ( origin != null && !origin.equals("null") ) {
            try {
                URI originUri = new URI(origin);
                String host = request.getServerName();
                if ( !(isLoopbackName(originUri.getHost()) || originUri.getHost().equalsIgnoreCase(host)) || originUri.getPort() != request.getServerPort() ) {
                    return answer(Response.Status.FORBIDDEN, false, "foreign origin");
                }
            } catch ( Exception e ) {
                return answer(Response.Status.FORBIDDEN, false, "invalid origin");
            }
        }
        return null;
    }

    private static boolean isLoopback(String address) {
        return address != null && (address.startsWith("127.") || address.equals("0:0:0:0:0:0:0:1") || address.equals("::1"));
    }

    private static boolean isLoopbackName(String host) {
        return host != null && (host.equalsIgnoreCase("localhost") || isLoopback(host) || host.equals("[::1]"));
    }

    private static boolean isKnownPort(String name) {
        return findPort(name) != null;
    }

    private static String portPath(SerialPort port) {
        String path = port.getSystemPortPath();
        return path == null ? port.getSystemPortName() : path;
    }

    static String portArgument(String port, boolean windows) {
        if ( windows ) {
            // COM10 and higher need the device namespace prefix
            return port.matches("(?i)COM\\d{2,}") ? "\\\\.\\" + port : port;
        }
        return port.startsWith("/") ? port : "/dev/" + port;
    }

    /**
     * the compiled code may be prefixed (e.g. with "null" for robots without hex prefix). The intel hex data starts at the first ':'.
     */
    static String extractHex(String decoded) {
        int start = decoded.indexOf(':');
        if ( start < 0 ) {
            throw new IllegalArgumentException("no intel hex data found");
        }
        return decoded.substring(start);
    }

    private static String tail(String text, int maxLength) {
        return text.length() <= maxLength ? text : "..." + text.substring(text.length() - maxLength);
    }

    private static Response answer(Response.Status status, boolean ok, String message) {
        JSONObject json = new JSONObject();
        json.put("rc", ok ? "ok" : "error");
        json.put("message", message);
        return Response.status(status).entity(json.toString()).type(MediaType.APPLICATION_JSON).build();
    }

    /**
     * avrdude parameters of the supported boards. The Nano is tried with the new (115200) and the old (57600) bootloader.
     */
    enum AvrdudeTarget {
        UNO("atmega328p", "arduino", 115200),
        NANO("atmega328p", "arduino", 115200, 57600),
        MEGA("atmega2560", "wiring", 115200);

        final String part;
        final String programmer;
        final int[] baudrates;

        AvrdudeTarget(String part, String programmer, int... baudrates) {
            this.part = part;
            this.programmer = programmer;
            this.baudrates = baudrates;
        }

        static AvrdudeTarget forRobot(String robot) {
            switch ( robot ) {
                case "uno":
                    return UNO;
                case "nano":
                    return NANO;
                case "mega":
                    return MEGA;
                default:
                    return null;
            }
        }
    }
}
