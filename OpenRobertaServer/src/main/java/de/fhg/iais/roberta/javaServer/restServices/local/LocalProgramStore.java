package de.fhg.iais.roberta.javaServer.restServices.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fhg.iais.roberta.util.Util;

/**
 * BlockCore keeps the programs and the named robot configurations of its single, local user in files (no database):
 *
 * <pre>
 * &lt;dir&gt;/&lt;program name&gt;.xml                      a program with its configuration, in the export format of the lab
 * &lt;dir&gt;/configurations/&lt;configuration name&gt;.xml   a named robot configuration
 * </pre>
 *
 * The program files can be opened with "import" in every Open Roberta Lab, and files copied into the directory appear in the program list.
 * Names are the names of the lab (letters, digits and _, see {@link Util#isValidJavaIdentifier(String)}), so they are valid file names on every
 * system. Files are written to a temporary file first and then renamed: a crash never leaves half a program.
 */
public final class LocalProgramStore {
    private static final Logger LOG = LoggerFactory.getLogger(LocalProgramStore.class);
    private static final String SUFFIX = ".xml";
    private static final String CONFIGURATIONS = "configurations";
    private static final Pattern PROGRAM = Pattern.compile("<program>\\s*(.*?)\\s*</program>", Pattern.DOTALL);
    private static final Pattern CONFIG = Pattern.compile("<config>\\s*(.*?)\\s*</config>", Pattern.DOTALL);

    private static volatile LocalProgramStore instance;

    private final Path dir;

    public LocalProgramStore(Path dir) {
        this.dir = dir;
    }

    /** the store of this server; set once at startup by {@link #init(Path)} */
    public static LocalProgramStore get() {
        LocalProgramStore store = instance;
        if ( store == null ) {
            throw new IllegalStateException("the program store is not initialised");
        }
        return store;
    }

    public static LocalProgramStore init(Path dir) {
        instance = new LocalProgramStore(dir);
        try {
            Files.createDirectories(dir.resolve(CONFIGURATIONS));
        } catch ( IOException e ) {
            LOG.error("the program directory " + dir + " cannot be created", e);
        }
        LOG.info("programs are stored in " + dir);
        return instance;
    }

    public Path getDir() {
        return this.dir;
    }

    /** a stored program: its name, the XML of the program and of its configuration (null: the default configuration) and its file times */
    public static final class StoredProgram {
        public final String name;
        public final String programXml;
        public final String configXml;
        public final long created;
        public final long lastChanged;

        StoredProgram(String name, String programXml, String configXml, long created, long lastChanged) {
            this.name = name;
            this.programXml = programXml;
            this.configXml = configXml;
            this.created = created;
            this.lastChanged = lastChanged;
        }
    }

    /** name and file times of a stored program or configuration */
    public static final class Entry {
        public final String name;
        public final long created;
        public final long lastChanged;

        Entry(String name, long created, long lastChanged) {
            this.name = name;
            this.created = created;
            this.lastChanged = lastChanged;
        }
    }

    public static boolean isValidName(String name) {
        return name != null && name.length() <= 255 && Util.isValidJavaIdentifier(name);
    }

    // ---------------------------------------------------------------- programs

    public boolean programExists(String name) {
        return isValidName(name) && Files.isRegularFile(programFile(name));
    }

    /** @return the program or null, if it does not exist or cannot be read */
    public StoredProgram loadProgram(String name) {
        if ( !programExists(name) ) {
            return null;
        }
        Path file = programFile(name);
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            String programXml = part(PROGRAM, text);
            if ( programXml == null ) {
                // a file that contains only the program (no export envelope)
                programXml = text.trim();
            }
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new StoredProgram(name, programXml, part(CONFIG, text), attributes.creationTime().toMillis(), attributes.lastModifiedTime().toMillis());
        } catch ( IOException e ) {
            LOG.error("the program " + file + " cannot be read", e);
            return null;
        }
    }

    /**
     * stores a program (a new one or a new version)
     *
     * @param configXml the configuration of the program; null: the default configuration
     * @return the time of the change
     */
    public long saveProgram(String name, String programXml, String configXml) throws IOException {
        check(name);
        StringBuilder export = new StringBuilder();
        export.append("<export xmlns=\"http://de.fhg.iais.roberta.blockly\"><program>").append(programXml).append("</program>");
        if ( configXml != null ) {
            export.append("<config>").append(configXml).append("</config>");
        }
        export.append("</export>");
        return write(programFile(name), export.toString());
    }

    /** sets the time of the last change (used when programs are taken over from the database of an older BlockCore) */
    public void setLastChanged(String name, long time) throws IOException {
        check(name);
        Files.setLastModifiedTime(programFile(name), FileTime.fromMillis(time));
    }

    public boolean deleteProgram(String name) {
        try {
            return isValidName(name) && Files.deleteIfExists(programFile(name));
        } catch ( IOException e ) {
            LOG.error("the program " + name + " cannot be deleted", e);
            return false;
        }
    }

    /** all programs, sorted by name (ignoring case) */
    public List<Entry> listPrograms() {
        return list(this.dir);
    }

    // ---------------------------------------------------------------- named configurations

    public boolean configurationExists(String name) {
        return isValidName(name) && Files.isRegularFile(configurationFile(name));
    }

    /** @return the XML of the configuration or null, if it does not exist */
    public String loadConfiguration(String name) {
        if ( !configurationExists(name) ) {
            return null;
        }
        try {
            return new String(Files.readAllBytes(configurationFile(name)), StandardCharsets.UTF_8).trim();
        } catch ( IOException e ) {
            LOG.error("the configuration " + name + " cannot be read", e);
            return null;
        }
    }

    public long saveConfiguration(String name, String configXml) throws IOException {
        check(name);
        return write(configurationFile(name), configXml);
    }

    public boolean deleteConfiguration(String name) {
        try {
            return isValidName(name) && Files.deleteIfExists(configurationFile(name));
        } catch ( IOException e ) {
            LOG.error("the configuration " + name + " cannot be deleted", e);
            return false;
        }
    }

    public List<Entry> listConfigurations() {
        return list(this.dir.resolve(CONFIGURATIONS));
    }

    // ---------------------------------------------------------------- helpers

    private Path programFile(String name) {
        return this.dir.resolve(name + SUFFIX);
    }

    private Path configurationFile(String name) {
        return this.dir.resolve(CONFIGURATIONS).resolve(name + SUFFIX);
    }

    private static void check(String name) {
        if ( !isValidName(name) ) {
            throw new IllegalArgumentException("invalid name: " + name);
        }
    }

    private static String part(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static long write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.write(part, text.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch ( AtomicMoveNotSupportedException e ) {
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return Files.getLastModifiedTime(file).toMillis();
    }

    private static List<Entry> list(Path directory) {
        List<Entry> entries = new ArrayList<>();
        if ( !Files.isDirectory(directory) ) {
            return entries;
        }
        try ( DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + SUFFIX) ) {
            for ( Path file : files ) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - SUFFIX.length());
                if ( isValidName(name) && Files.isRegularFile(file) ) {
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                    entries.add(new Entry(name, attributes.creationTime().toMillis(), attributes.lastModifiedTime().toMillis()));
                }
            }
        } catch ( IOException e ) {
            LOG.error("the directory " + directory + " cannot be read", e);
        }
        Collections.sort(entries, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return entries;
    }

    /** the directory for programs: the property server.local.programs.dir, otherwise &lt;server.admin.dir&gt;/programs */
    public static Path directoryFor(String programsDir, String adminDir) {
        if ( programsDir != null && !programsDir.trim().isEmpty() ) {
            return Paths.get(programsDir.trim());
        }
        return Paths.get(adminDir == null || adminDir.trim().isEmpty() ? "." : adminDir.trim(), "programs");
    }
}
