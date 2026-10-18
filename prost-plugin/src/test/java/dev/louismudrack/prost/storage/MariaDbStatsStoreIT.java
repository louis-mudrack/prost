package dev.louismudrack.prost.storage;

import dev.louismudrack.prost.config.PluginConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static dev.louismudrack.prost.storage.StatsField.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class MariaDbStatsStoreIT {
    private static MariaDBContainer<?> db;

    @BeforeAll
    static void start() {
        db = new MariaDBContainer<>("mariadb:11.4");
        db.start();
    }

    @AfterAll
    static void stop() { db.stop(); }

    private static SqlStatsStore store() throws Exception {
        PluginConfig.Storage s = new PluginConfig.Storage("mysql", db.getHost(), db.getMappedPort(3306), db.getDatabaseName(),
                db.getUsername(), db.getPassword(), "it_", "useSSL=false");
        SqlStatsStore store = SqlStatsStore.mysql(s, Logger.getLogger("it"), Runnable::run);
        store.open();
        return store;
    }

    private static StatsRow row(SqlStatsStore s, UUID id) throws Exception {
        CompletableFuture<StatsRow> f = new CompletableFuture<>();
        s.row(id, f::complete);
        return f.get(10, TimeUnit.SECONDS);
    }

    @Test
    void twoServersAddUp() throws Exception {
        UUID id = UUID.randomUUID();
        SqlStatsStore a = store(), b = store();
        for (int i = 0; i < 100; i++) {
            a.add(id, "P", DRUNK, 1);
            b.add(id, "P", DRUNK, 1);
        }
        a.flush().get(10, TimeUnit.SECONDS);
        b.flush().get(10, TimeUnit.SECONDS);
        assertEquals(200, row(a, id).drunk());
        a.close();
        b.close();
    }

    @Test
    void openingTwiceKeepsTheSchemaAndTheData() throws Exception {
        UUID id = UUID.randomUUID();
        SqlStatsStore first = store();
        first.add(id, "P", ROUNDS, 4);
        first.flush().get(10, TimeUnit.SECONDS);
        first.close();
        SqlStatsStore second = store();
        assertEquals(4, row(second, id).rounds());
        second.close();
    }

    @Test
    void reconnectsAfterKill() throws Exception {
        UUID id = UUID.randomUUID();
        SqlStatsStore s = store();
        s.add(id, "P", WON, 1);
        s.flush().get(10, TimeUnit.SECONDS);
        try (Connection root = DriverManager.getConnection(db.getJdbcUrl(), "root", db.getPassword());
             Statement st = root.createStatement()) {
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("SELECT id FROM information_schema.processlist WHERE user = '" + db.getUsername() + "'")) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
            for (long pid : ids) st.execute("KILL " + pid);
        }
        s.add(id, "P", WON, 1);
        s.flush().get(10, TimeUnit.SECONDS);
        assertEquals(2, row(s, id).won());
        s.close();
    }
}
