package de.fhg.iais.roberta.javaServer.restServices.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mockito;

import de.fhg.iais.roberta.generated.restEntities.FullRestRequest;
import de.fhg.iais.roberta.javaServer.restServices.local.LocalProgramStore.Entry;
import de.fhg.iais.roberta.javaServer.restServices.local.LocalProgramStore.StoredProgram;
import de.fhg.iais.roberta.persistence.util.HttpSessionState;
import de.fhg.iais.roberta.util.ServerProperties;

public class LocalProgramStoreTest {
    private static final String PROGRAM = "<xml xmlns=\"http://de.fhg.iais.roberta.blockly\" robottype=\"arduino\"><block type=\"robControls_start\"/></xml>";
    private static final String CONFIG = "<xml xmlns=\"http://de.fhg.iais.roberta.blockly\" robottype=\"arduino\"><block type=\"robBrick_arduino\"/></xml>";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path dir;
    private LocalProgramStore store;

    @Before
    public void setUp() throws Exception {
        this.dir = this.temporaryFolder.newFolder("BlockCore").toPath();
        this.store = LocalProgramStore.init(this.dir);
    }

    @Test
    public void programs_areSavedLoadedListedAndDeleted() throws Exception {
        this.store.saveProgram("Linefollower", PROGRAM, CONFIG);
        this.store.saveProgram("blink", PROGRAM, null);

        StoredProgram loaded = this.store.loadProgram("Linefollower");
        assertEquals(PROGRAM, loaded.programXml);
        assertEquals(CONFIG, loaded.configXml);
        assertNull(this.store.loadProgram("blink").configXml);
        // the file is an export of the lab: it can be imported everywhere
        String file = new String(Files.readAllBytes(this.dir.resolve("Linefollower.xml")), StandardCharsets.UTF_8);
        assertTrue(file.startsWith("<export xmlns=\"http://de.fhg.iais.roberta.blockly\"><program>"));

        List<Entry> programs = this.store.listPrograms();
        assertEquals(2, programs.size());
        assertEquals("blink", programs.get(0).name);
        assertEquals("Linefollower", programs.get(1).name);

        assertTrue(this.store.deleteProgram("blink"));
        assertFalse(this.store.programExists("blink"));
        assertFalse(this.store.deleteProgram("blink"));
        assertFalse(Files.exists(this.dir.resolve("Linefollower.xml.part")));
    }

    @Test
    public void names_mustBeNamesOfTheLab() {
        assertTrue(LocalProgramStore.isValidName("NEPOprog_2"));
        assertFalse(LocalProgramStore.isValidName("../secret"));
        assertFalse(LocalProgramStore.isValidName("a b"));
        assertFalse(LocalProgramStore.isValidName(""));
        assertFalse(LocalProgramStore.isValidName(null));
        assertNull(this.store.loadProgram("../x"));
    }

    @Test
    public void filesCopiedIntoTheDirectory_appearInTheList() throws Exception {
        String export = "<export xmlns=\"http://de.fhg.iais.roberta.blockly\"><program>" + PROGRAM + "</program><config>" + CONFIG + "</config></export>";
        Files.write(this.dir.resolve("FromAFriend.xml"), export.getBytes(StandardCharsets.UTF_8));
        Files.write(this.dir.resolve("not a program.xml"), export.getBytes(StandardCharsets.UTF_8));
        assertEquals(1, this.store.listPrograms().size());
        assertEquals(CONFIG, this.store.loadProgram("FromAFriend").configXml);
    }

    @Test
    public void configurations_areSavedLoadedListedAndDeleted() throws Exception {
        this.store.saveConfiguration("myRobot", CONFIG);
        assertEquals(CONFIG, this.store.loadConfiguration("myRobot"));
        assertEquals(1, this.store.listConfigurations().size());
        assertEquals(0, this.store.listPrograms().size());
        assertTrue(this.store.deleteConfiguration("myRobot"));
        assertNull(this.store.loadConfiguration("myRobot"));
    }

    @Test
    public void controllers_saveListAndDeletePrograms() throws Exception {
        HttpSessionState session = HttpSessionState.init(Collections.emptyMap(), Mockito.mock(ServerProperties.class), "", "");
        LocalUserController user = new LocalUserController();
        LocalProgramController programs = new LocalProgramController();

        assertEquals("error", rc(programs.getInfosOfProgramsOfLoggedInUser(request(session, new JSONObject()))));
        assertEquals("ok", rc(user.login(request(session, new JSONObject().put("accountName", "blockcore").put("password", "x")))));
        assertTrue(session.isUserLoggedIn());

        JSONObject save =
            new JSONObject().put("cmd", "saveAs").put("programName", "Robo").put("ownerAccount", "blockcore").put("progXML", PROGRAM).put("confXML", CONFIG);
        assertEquals("ok", rc(programs.saveProgram(request(session, save))));
        assertEquals("error", rc(programs.saveProgram(request(session, save)))); // save as: the program exists
        assertEquals("ok", rc(programs.saveProgram(request(session, save.put("cmd", "save")))));
        assertEquals("error", rc(programs.saveProgram(request(session, save.put("programName", "no name")))));

        JSONObject list = entity(programs.getInfosOfProgramsOfLoggedInUser(request(session, new JSONObject())));
        JSONArray names = list.getJSONArray("programNames");
        assertEquals(1, names.length());
        assertEquals("Robo", names.getJSONArray(0).getString(0));
        assertEquals("blockcore", names.getJSONArray(0).getString(1));

        assertEquals("ok", rc(programs.deleteProgram(request(session, new JSONObject().put("programName", "Robo").put("author", "blockcore")))));
        assertEquals(0, entity(programs.getInfosOfProgramsOfLoggedInUser(request(session, new JSONObject()))).getJSONArray("programNames").length());
    }

    @Test
    public void migration_copiesTheProgramsOfTheOldDatabaseOnce() throws Exception {
        String database = this.temporaryFolder.newFolder("db").toPath().resolve("openroberta-db").toString();
        try ( Connection connection = DriverManager.getConnection("jdbc:hsqldb:file:" + database, "SA", "");
            Statement sql = connection.createStatement() ) {
            sql.execute("create table USER (ID integer, USERGROUP_ID integer, ACCOUNT varchar(255))");
            sql.execute("create table PROGRAM (NAME varchar(255), OWNER_ID integer, PROGRAM_TEXT varchar(100000), CONFIG_NAME varchar(255), CONFIG_HASH varchar(255), LAST_CHANGED timestamp)");
            sql.execute("create table CONFIGURATION (NAME varchar(255), OWNER_ID integer, CONFIGURATION_HASH varchar(255), LAST_CHANGED timestamp)");
            sql.execute("create table CONFIGURATION_DATA (CONFIGURATION_HASH varchar(255), CONFIGURATION_TEXT varchar(100000))");
            sql.execute("insert into USER values (1, null, 'Roberta'), (3, null, 'blockcore')");
            sql.execute("insert into CONFIGURATION_DATA values ('h1', '" + CONFIG + "')");
            sql.execute("insert into CONFIGURATION values ('namedConf', 3, 'h1', timestamp '2026-01-02 10:00:00')");
            sql.execute("insert into PROGRAM values ('anonymous', 3, '" + PROGRAM + "', null, 'h1', timestamp '2026-01-03 11:00:00')");
            sql.execute("insert into PROGRAM values ('named', 3, '" + PROGRAM + "', 'namedConf', null, timestamp '2026-01-03 12:00:00')");
            sql.execute("insert into PROGRAM values ('withDefault', 3, '" + PROGRAM + "', null, null, timestamp '2026-01-03 13:00:00')");
            sql.execute("insert into PROGRAM values ('ofRoberta', 1, '" + PROGRAM + "', null, null, timestamp '2026-01-03 13:00:00')");
            sql.execute("SHUTDOWN");
        }
        this.store.saveProgram("withDefault", "<xml>newer</xml>", null); // exists already: is kept

        assertEquals(2, LocalDatabaseMigration.migrateIfNeeded(database, this.store));
        assertEquals(CONFIG, this.store.loadProgram("anonymous").configXml);
        assertEquals(CONFIG, this.store.loadProgram("named").configXml);
        assertEquals("<xml>newer</xml>", this.store.loadProgram("withDefault").programXml);
        assertFalse(this.store.programExists("ofRoberta"));
        assertEquals(CONFIG, this.store.loadConfiguration("namedConf"));
        assertEquals(java.sql.Timestamp.valueOf("2026-01-03 11:00:00").getTime(), this.store.loadProgram("anonymous").lastChanged);
        // once only
        assertEquals(-1, LocalDatabaseMigration.migrateIfNeeded(database, this.store));
        assertEquals(-1, LocalDatabaseMigration.migrateIfNeeded(null, this.store));
    }

    private static FullRestRequest request(HttpSessionState session, JSONObject data) {
        return FullRestRequest.make().setInitToken(session.getInitToken()).setData(data).setLog(Collections.emptyList()).immutable();
    }

    private static JSONObject entity(Response response) {
        Object entity = response.getEntity();
        return entity instanceof JSONObject ? (JSONObject) entity : new JSONObject(entity.toString());
    }

    private static String rc(Response response) {
        return entity(response).getString("rc");
    }
}
