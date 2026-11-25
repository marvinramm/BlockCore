package de.fhg.iais.roberta.javaServer.restServices.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BlockCore up to version 1.2 kept the programs in a database (HyperSQL). On the first start of a newer version the programs and named
 * configurations of the local user are copied from that database into the program files ({@link LocalProgramStore}), once. The database is
 * kept as it is (a file "&lt;database&gt;.copied-to-files" marks that it was copied). Programs that already exist as files are not overwritten.
 */
public final class LocalDatabaseMigration {
    private static final Logger LOG = LoggerFactory.getLogger(LocalDatabaseMigration.class);
    /** the account of the local user in the database (created by blockcore.js) */
    static final String OLD_ACCOUNT = "blockcore";

    private LocalDatabaseMigration() {
    }

    /**
     * @param database path of the database without extension, e.g. %LOCALAPPDATA%\BlockCore\db\openroberta-db; null: nothing to do
     * @return the number of programs copied, -1 if there was nothing to copy
     */
    public static int migrateIfNeeded(String database, LocalProgramStore store) {
        if ( database == null || database.trim().isEmpty() ) {
            return -1;
        }
        Path script = Paths.get(database + ".script");
        Path marker = Paths.get(database + ".copied-to-files");
        if ( !Files.isRegularFile(script) || Files.exists(marker) ) {
            return -1;
        }
        try ( Connection connection = connect(database) ) {
            int programs = copy(connection, store);
            try ( Statement shutdown = connection.createStatement() ) {
                shutdown.execute("SHUTDOWN");
            }
            Files.write(marker, ("copied " + programs + " programs to " + store.getDir() + "\n").getBytes(StandardCharsets.UTF_8));
            LOG.info("{} programs were copied from the database {} to {}", programs, database, store.getDir());
            return programs;
        } catch ( SQLException | IOException e ) {
            LOG.error("the programs could not be copied from the database " + database, e);
            return 0;
        }
    }

    /** the database of the lab has the user orA (see the former hibernate-cfg.xml); a database created otherwise has the default user SA */
    private static Connection connect(String database) throws SQLException {
        String url = "jdbc:hsqldb:file:" + database + ";ifexists=true";
        try {
            return DriverManager.getConnection(url, "orA", "Pid");
        } catch ( SQLException e ) {
            return DriverManager.getConnection(url, "SA", "");
        }
    }

    static int copy(Connection connection, LocalProgramStore store) throws SQLException, IOException {
        Integer userId = userId(connection);
        if ( userId == null ) {
            return 0;
        }
        // named configurations first: programs may refer to them
        try ( PreparedStatement select =
            connection
                .prepareStatement(
                    "select c.NAME, d.CONFIGURATION_TEXT, c.LAST_CHANGED from CONFIGURATION c join CONFIGURATION_DATA d on c.CONFIGURATION_HASH = d.CONFIGURATION_HASH where c.OWNER_ID = ?") ) {
            select.setInt(1, userId);
            try ( ResultSet rows = select.executeQuery() ) {
                while ( rows.next() ) {
                    String name = rows.getString(1);
                    if ( LocalProgramStore.isValidName(name) && !store.configurationExists(name) ) {
                        store.saveConfiguration(name, rows.getString(2));
                    }
                }
            }
        }
        int programs = 0;
        try ( PreparedStatement select =
            connection.prepareStatement("select NAME, PROGRAM_TEXT, CONFIG_NAME, CONFIG_HASH, LAST_CHANGED from PROGRAM where OWNER_ID = ?") ) {
            select.setInt(1, userId);
            try ( ResultSet rows = select.executeQuery() ) {
                while ( rows.next() ) {
                    String name = rows.getString(1);
                    String programText = rows.getString(2);
                    if ( !LocalProgramStore.isValidName(name) || programText == null || store.programExists(name) ) {
                        continue;
                    }
                    String configName = rows.getString(3);
                    String configHash = rows.getString(4);
                    String configText = configName != null ? store.loadConfiguration(configName) : configHash != null ? configurationText(connection, configHash) : null;
                    store.saveProgram(name, programText, configText);
                    Timestamp lastChanged = rows.getTimestamp(5);
                    if ( lastChanged != null ) {
                        store.setLastChanged(name, lastChanged.getTime());
                    }
                    programs++;
                }
            }
        }
        return programs;
    }

    private static Integer userId(Connection connection) throws SQLException {
        try ( PreparedStatement select = connection.prepareStatement("select ID from USER where ACCOUNT = ? and USERGROUP_ID is null") ) {
            select.setString(1, OLD_ACCOUNT);
            try ( ResultSet rows = select.executeQuery() ) {
                return rows.next() ? rows.getInt(1) : null;
            }
        }
    }

    private static String configurationText(Connection connection, String hash) throws SQLException {
        try ( PreparedStatement select = connection.prepareStatement("select CONFIGURATION_TEXT from CONFIGURATION_DATA where CONFIGURATION_HASH = ?") ) {
            select.setString(1, hash);
            try ( ResultSet rows = select.executeQuery() ) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }
}
