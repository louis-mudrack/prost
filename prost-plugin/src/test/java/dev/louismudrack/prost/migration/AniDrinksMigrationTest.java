package dev.louismudrack.prost.migration;

import dev.louismudrack.prost.storage.SqlStatsStore;
import dev.louismudrack.prost.storage.StatsRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static dev.louismudrack.prost.migration.OldInstall.KASHEYY;
import static org.junit.jupiter.api.Assertions.*;

class AniDrinksMigrationTest {
    private static final Logger LOG = Logger.getLogger("migration-test");

    @TempDir Path plugins;
    private Path prost;
    private SqlStatsStore store;

    @BeforeEach
    void open() throws Exception {
        prost = Files.createDirectories(plugins.resolve("Prost"));
        store = SqlStatsStore.sqlite(prost.resolve("stats.db").toFile(), "prost_", LOG, Runnable::run);
        store.open();
    }

    @AfterEach
    void close() { store.close(); }

    private AniDrinksMigration migration() { return new AniDrinksMigration(plugins.toFile(), prost.toFile(), store, LOG); }

    private AniDrinksMigration.Result run() throws Exception { return migration().run(); }

    private StatsRow row(UUID id) throws Exception {
        CompletableFuture<StatsRow> f = new CompletableFuture<>();
        store.row(id, f::complete);
        return f.get(5, TimeUnit.SECONDS);
    }

    @Test
    void takesEverythingOver() throws Exception {
        OldInstall.write(plugins);
        AniDrinksMigration.Result r = run();
        assertEquals(2, r.players());
        assertEquals(1, r.rounds());
        assertEquals(List.of("challenges.yml", "rules.yml", "game.yml", "rounds.yml"), r.files());
        assertEquals("AniDrinks.migrated", r.renamedTo());
        assertEquals("max-players: 6\n", Files.readString(prost.resolve("game.yml")));
        assertFalse(Files.exists(plugins.resolve("AniDrinks")));
        assertTrue(Files.isRegularFile(plugins.resolve("AniDrinks.migrated").resolve("stats.db")));
        StatsRow k = row(KASHEYY);
        assertEquals(26, k.drunk());
        assertEquals(6, k.roulette());
        assertEquals(2, k.rounds());
    }

    @Test
    void withoutAniDrinksThereIsNothingToDo() throws Exception {
        assertFalse(migration().found());
        assertNull(run());
    }

    @Test
    void aSecondRunFindsNothing() throws Exception {
        OldInstall.write(plugins);
        assertNotNull(run());
        assertFalse(migration().found());
        assertNull(run());
        assertEquals(26, row(KASHEYY).drunk());
    }

    @Test
    void aCrashHalfwayRepeatsSafely() throws Exception {
        OldInstall.write(plugins);
        Files.writeString(prost.resolve("game.yml"), "max-players: 4\n");
        store.importRow(new StatsRow(KASHEYY, "Kasheyy", 26, 0, 12, 6, 3, 6, 1, 1, 2), 1).get(5, TimeUnit.SECONDS);
        AniDrinksMigration.Result r = run();
        assertEquals("max-players: 4\n", Files.readString(prost.resolve("game.yml")), "a file Prost already has is kept");
        assertFalse(r.files().contains("game.yml"));
        assertEquals(26, row(KASHEYY).drunk(), "absolute values: not doubled");
    }

    @Test
    void aFailureMidwayRenamesNothing() throws Exception {
        Path old = OldInstall.write(plugins);
        OldInstall.breakRounds(old);
        assertThrows(SQLException.class, this::run);
        assertEquals(26, row(KASHEYY).drunk(), "the stats were in before the rounds failed");
        assertTrue(Files.isRegularFile(old.resolve("stats.db")), "AniDrinks stays as it is for the next try");
        assertTrue(Files.isRegularFile(old.resolve("drinks.yml")));
        assertFalse(Files.exists(plugins.resolve("AniDrinks.migrated")));

        OldInstall.repairRounds(old);
        AniDrinksMigration.Result retry = run();
        assertEquals(1, retry.rounds());
        assertEquals(26, row(KASHEYY).drunk(), "the retry is not doubled");
        assertTrue(Files.isDirectory(plugins.resolve("AniDrinks.migrated")));
    }

    @Test
    void knowsWhetherProstHoldsStats() throws Exception {
        assertFalse(migration().prostHasStats());
        store.importRow(new StatsRow(KASHEYY, "Kasheyy", 1, 0, 0, 0, 0, 0, 0, 0, 1), 1).get(5, TimeUnit.SECONDS);
        assertTrue(migration().prostHasStats());
    }

    /** an empty table-prefix names Prost's tables like AniDrinks' own: fine in a database of Prost's own */
    @Test
    void anEmptyPrefixWorksInProstsOwnDatabase() throws Exception {
        OldInstall.write(plugins);
        store.close();
        store = SqlStatsStore.sqlite(prost.resolve("unprefixed.db").toFile(), "", LOG, Runnable::run);
        store.open();
        assertEquals(2, run().players());
        assertEquals(26, row(KASHEYY).drunk());
    }

    /** ...but a database already holding AniDrinks' tables (a copied stats.db) is refused, and nothing is renamed */
    @Test
    void anEmptyPrefixNeverWritesIntoAniDrinksTables() throws Exception {
        Path old = OldInstall.write(plugins);
        Path copied = Files.copy(old.resolve("stats.db"), prost.resolve("copied.db"));
        store.close();
        store = SqlStatsStore.sqlite(copied.toFile(), "", LOG, Runnable::run);
        SQLException refused = assertThrows(SQLException.class, store::open);
        assertTrue(refused.getMessage().contains("storage.table-prefix"), refused.getMessage());

        assertThrows(SQLException.class, this::run);
        assertTrue(Files.isRegularFile(old.resolve("stats.db")), "nothing renamed");
        assertFalse(Files.exists(plugins.resolve("AniDrinks.migrated")));
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + copied); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeQuery("SELECT language FROM stats"), "the old table got no Prost columns");
        }
    }
}
