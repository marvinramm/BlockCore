package de.fhg.iais.roberta.javaServer.restServices.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Update of the installed BlockCore (Windows): downloads the installer of the latest release from GitHub and hands it over to the
 * launcher (BlockCore.exe). The launcher is told by the exit code {@link #EXIT_CODE_UPDATE} of the server; it closes the BlockCore
 * windows, starts the installer (it replaces the files of the running server, so the server must not run any more) and the installer
 * starts BlockCore again.<br>
 * <br>
 * Only the installer of the latest release of the BlockCore repository is downloaded; the URL comes from GitHub, not from the browser.
 * The update is offered only if the launcher passed the property <code>server.local.update.dir</code> (BlockCore was installed with the
 * installer, not unzipped).
 */
final class LocalUpdate {
    static final int EXIT_CODE_UPDATE = 42;
    private static final Logger LOG = LoggerFactory.getLogger(LocalUpdate.class);
    private static final String LATEST_RELEASE = "https://api.github.com/repos/marvinramm/BlockCore/releases/latest";
    private static final String DOWNLOAD_PREFIX = "https://github.com/marvinramm/BlockCore/releases/download/";
    private static final String INSTALLER = "BlockCore-Setup.exe";
    private static final String PENDING_FILE = "pending.txt";
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern VERSION_JSON = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]*)\"");

    enum State {
        IDLE, DOWNLOADING, READY, FAILED
    }

    private static volatile State state = State.IDLE;
    private static volatile long done;
    private static volatile long total;
    private static volatile String version = "";
    private static volatile String message = "";

    private LocalUpdate() {
    }

    static JSONObject status(boolean canInstall) {
        JSONObject json = new JSONObject();
        json.put("canInstall", canInstall);
        json.put("state", state.name());
        json.put("percent", total > 0 ? Math.min(100, (int) (done * 100 / total)) : 0);
        json.put("version", version);
        json.put("message", message);
        return json;
    }

    /**
     * starts the download in the background (once)
     *
     * @param updateDir directory for the installer, from the launcher
     * @param staticResources directory of the web frontend (it contains the version of this BlockCore)
     */
    static synchronized void start(Path updateDir, Path staticResources) {
        if ( state == State.DOWNLOADING || state == State.READY ) {
            return;
        }
        state = State.DOWNLOADING;
        done = 0;
        total = 0;
        message = "";
        Thread thread = new Thread(() -> download(updateDir, staticResources), "BlockCore update");
        thread.setDaemon(true);
        thread.start();
    }

    private static void download(Path updateDir, Path staticResources) {
        try {
            HttpClient client =
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
            HttpResponse<String> latest =
                client
                    .send(
                        HttpRequest.newBuilder(URI.create(LATEST_RELEASE)).header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(20)).build(),
                        HttpResponse.BodyHandlers.ofString());
            if ( latest.statusCode() != 200 ) {
                throw new IOException("GitHub answered " + latest.statusCode());
            }
            JSONObject release = new JSONObject(latest.body());
            String tag = release.optString("tag_name", "");
            int[] available = parse(tag);
            int[] installed = parse(currentVersion(staticResources));
            if ( available == null ) {
                throw new IOException("the latest release has no version: " + tag);
            }
            if ( installed != null && compare(available, installed) <= 0 ) {
                throw new IOException("BlockCore is up to date");
            }
            String url = null;
            long size = 0;
            JSONArray assets = release.optJSONArray("assets");
            for ( int i = 0; assets != null && i < assets.length(); i++ ) {
                JSONObject asset = assets.getJSONObject(i);
                if ( INSTALLER.equals(asset.optString("name")) ) {
                    url = asset.optString("browser_download_url");
                    size = asset.optLong("size");
                }
            }
            if ( url == null || !url.startsWith(DOWNLOAD_PREFIX) ) {
                throw new IOException("the latest release has no installer");
            }
            version = tag.startsWith("v") ? tag.substring(1) : tag;
            total = size;
            Files.createDirectories(updateDir);
            Path target = updateDir.resolve("BlockCore-Setup-" + version + ".exe");
            Path part = updateDir.resolve(target.getFileName() + ".part");
            HttpResponse<InputStream> answer =
                client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).build(), HttpResponse.BodyHandlers.ofInputStream());
            if ( answer.statusCode() != 200 ) {
                throw new IOException("download failed: " + answer.statusCode());
            }
            if ( total <= 0 ) {
                total = answer.headers().firstValueAsLong("Content-Length").orElse(0);
            }
            try (InputStream in = answer.body(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ( (read = in.read(buffer)) > 0 ) {
                    out.write(buffer, 0, read);
                    done += read;
                }
            }
            if ( total > 0 && Files.size(part) != total ) {
                throw new IOException("download incomplete");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            Files.write(updateDir.resolve(PENDING_FILE), target.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
            state = State.READY;
            LOG.info("update {} downloaded to {}, the server stops for the installation", version, target);
            // give the browser time to show "installing", then hand over to the launcher
            Thread exit = new Thread(() -> {
                try {
                    Thread.sleep(2500);
                } catch ( InterruptedException e ) {
                    Thread.currentThread().interrupt();
                }
                System.exit(EXIT_CODE_UPDATE);
            }, "BlockCore update exit");
            exit.setDaemon(false);
            exit.start();
        } catch ( Exception e ) {
            LOG.warn("update failed", e);
            message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            state = State.FAILED;
        }
    }

    static String currentVersion(Path staticResources) {
        try {
            String json = new String(Files.readAllBytes(staticResources.resolve("blockcore").resolve("version.json")), StandardCharsets.UTF_8);
            Matcher matcher = VERSION_JSON.matcher(json);
            return matcher.find() ? matcher.group(1) : "";
        } catch ( IOException | RuntimeException e ) {
            return "";
        }
    }

    static Path path(String directory) {
        return directory == null || directory.trim().isEmpty() ? null : Paths.get(directory.trim());
    }

    private static int[] parse(String text) {
        Matcher matcher = VERSION.matcher(text == null ? "" : text.trim());
        return matcher.matches() ? new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))} : null;
    }

    private static int compare(int[] a, int[] b) {
        for ( int i = 0; i < 3; i++ ) {
            if ( a[i] != b[i] ) {
                return Integer.compare(a[i], b[i]);
            }
        }
        return 0;
    }
}
