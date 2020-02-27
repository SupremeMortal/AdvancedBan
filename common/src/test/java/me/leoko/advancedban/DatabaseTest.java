package me.leoko.advancedban;

import me.leoko.advancedban.manager.DatabaseManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Created by Leo on 07.08.2017.
 */

public class DatabaseTest {
    private AdvancedBan advancedBan;

    @BeforeAll
    @ExtendWith(TempDirectory.class)
    public void onEnable(@TempDirectory.TempDir Path dataFolder) throws IOException {
        advancedBan = new TestAdvancedBan(dataFolder);
        advancedBan.onEnable();
    }

    @Test
    public void shouldAutomaticallyDetectDatabaseType() {
        assertFalse(DatabaseManager.getInstance().isUseMySQL(), "By default no connection with MySQL should be established as it's disabled");
        assertFalse(DatabaseManager.getInstance().isFailedMySQL(), "MySQL should not be failed as it should not even try establishing any connection");
        assertTrue(DatabaseManager.getInstance().isConnectionValid(3), "The HSQLDB-Connection should be valid");
        DatabaseManager.getInstance().onDisable();
        DatabaseManager.getInstance().onEnable();
        assertFalse(DatabaseManager.getInstance().isUseMySQL(), "Because of a failed connection MySQL should be disabled");
        assertTrue(DatabaseManager.getInstance().isFailedMySQL(), "MySQL should be failed as the connection can not succeed");
    }

    @AfterAll
    public void onDisable() {
        advancedBan.onDisable();
    }
}