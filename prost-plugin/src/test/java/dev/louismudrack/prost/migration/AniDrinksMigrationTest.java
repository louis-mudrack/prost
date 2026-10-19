package dev.louismudrack.prost.migration;

import dev.louismudrack.prost.storage.SqlStatsStore;
import dev.louismudrack.prost.storage.StatsRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static dev.louismudrack.prost.migration.OldInstall.ALEX;
import static dev.louismudrack.prost.migration.OldInstall.KASHEYY;
import static org.junit.jupiter.api.Assertions.*;

class AniDrinksMigrationTest {
    private static final Logger LOG = Logger.getLogger("migration-test");
    private static final StatsRow KASHEYY_ROW = new StatsRow(KASHEYY, "Kasheyy", 26, 4, 12, 7, 3, 6, 1, 5, 2);
    private static final StatsRow ALEX_ROW = new StatsRow(ALEX, "Alex", 27, 9, 10, 8, 15, 13, 11, 14, 3);

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

    /** the jar's copies of the files Prost writes on its first start */
    private static InputStream shipped(String name) { return AniDrinksMigrationTest.class.getClassLoader().getResourceAsStream(name); }

    private static byte[] shippedBytes(String name) throws IOException {
        try (InputStream in = shipped(name)) { return in.readAllBytes(); }
    }

    private AniDrinksMigration migration() {
        return new AniDrinksMigration(plugins.toFile(), prost.toFile(), AniDrinksMigrationTest::shipped, store, LOG);
    }

    private AniDrinksMigration.Result run() throws Exception { return migration().run(); }

    private StatsRow row(UUID id) throws Exception {
        CompletableFuture<StatsRow> f = new CompletableFuture<>();
        store.row(id, f::complete);
        return f.get(5, TimeUnit.SECONDS);
    }

    private long lastPlayed(UUID id) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + prost.resolve("stats.db"));
             PreparedStatement ps = c.prepareStatement("SELECT last_played FROM prost_stats WHERE uuid = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "no row for " + id);
                return rs.getLong(1);
            }
        }
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
        assertEquals(KASHEYY_ROW, row(KASHEYY));
        assertEquals(ALEX_ROW, row(ALEX));
        assertEquals(1792283176874L, lastPlayed(KASHEYY));
        assertEquals(1792283176875L, lastPlayed(ALEX));
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
        assertEquals(KASHEYY_ROW, row(KASHEYY));
    }

    @Test
    void aCrashHalfwayRepeatsSafely() throws Exception {
        OldInstall.write(plugins);
        Files.writeString(prost.resolve("game.yml"), "max-players: 4\n");
        store.importRow(KASHEYY_ROW, 1).get(5, TimeUnit.SECONDS);
        AniDrinksMigration.Result r = run();
        assertEquals("max-players: 4\n", Files.readString(prost.resolve("game.yml")), "a file Prost already has is kept");
        assertFalse(r.files().contains("game.yml"));
        assertEquals(KASHEYY_ROW, row(KASHEYY), "absolute values: not doubled");
    }

    /** a start that took nothing over (AniDrinks.jar still there, the store refused) wrote Prost's shipped files */
    @Test
    void replacesProstsUntouchedDefaults() throws Exception {
        Path old = OldInstall.write(plugins);
        OldInstall.runningRound(old);
        for (String f : List.of("game.yml", "challenges.yml", "rules.yml")) Files.write(prost.resolve(f), shippedBytes(f));
        Files.writeString(prost.resolve("rounds.yml"), "[]\n");
        AniDrinksMigration.Result r = run();
        assertEquals(List.of("challenges.yml", "rules.yml", "game.yml", "rounds.yml"), r.files());
        assertEquals("max-players: 6\n", Files.readString(prost.resolve("game.yml")));
        assertEquals("mine_iron: {}\n", Files.readString(prost.resolve("challenges.yml")));
        assertEquals("- {de: a, en: b}\n", Files.readString(prost.resolve("rules.yml")));
        assertTrue(Files.readString(prost.resolve("rounds.yml")).contains("L9D5"));
    }

    @Test
    void keepsWhatTheAdminChanged() throws Exception {
        Path old = OldInstall.write(plugins);
        OldInstall.runningRound(old);
        Files.write(prost.resolve("game.yml"), shippedBytes("game.yml"));
        String ownRules = new String(shippedBytes("rules.yml"), StandardCharsets.UTF_8) + "# mine\n";
        Files.writeString(prost.resolve("rules.yml"), ownRules);
        Files.writeString(prost.resolve("rounds.yml"), "- code: ABCD\n");
        AniDrinksMigration.Result r = run();
        assertEquals(List.of("challenges.yml", "game.yml"), r.files());
        assertEquals(ownRules, Files.readString(prost.resolve("rules.yml")));
        assertEquals("- code: ABCD\n", Files.readString(prost.resolve("rounds.yml")), "a round of Prost's own is never replaced");
    }

    /** AniDrinks' catalogue has none of the entries Prost added since: CatalogueMerge appends them once the marker is gone */
    @Test
    void aCatalogueTakenOverDropsTheMarker() throws Exception {
        OldInstall.write(plugins);
        Files.write(prost.resolve("challenges.yml"), shippedBytes("challenges.yml"));
        Files.writeString(prost.resolve(".catalogue-version"), "0.4.0\n");
        run();
        assertEquals("mine_iron: {}\n", Files.readString(prost.resolve("challenges.yml")));
        assertFalse(Files.exists(prost.resolve(".catalogue-version")));
    }

    @Test
    void prostsOwnCatalogueKeepsItsMarker() throws Exception {
        OldInstall.write(plugins);
        String own = new String(shippedBytes("challenges.yml"), StandardCharsets.UTF_8) + "# mine\n";
        Files.writeString(prost.resolve("challenges.yml"), own);
        Files.writeString(prost.resolve(".catalogue-version"), "0.4.0\n");
        assertFalse(run().files().contains("challenges.yml"));
        assertEquals(own, Files.readString(prost.resolve("challenges.yml")));
        assertEquals("0.4.0\n", Files.readString(prost.resolve(".catalogue-version")));
    }

    @Test
    void aFailureMidwayRenamesNothing() throws Exception {
        Path old = OldInstall.write(plugins);
        OldInstall.breakRounds(old);
        assertThrows(SQLException.class, this::run);
        assertEquals(KASHEYY_ROW, row(KASHEYY), "the stats were in before the rounds failed");
        assertTrue(Files.isRegularFile(old.resolve("stats.db")), "AniDrinks stays as it is for the next try");
        assertTrue(Files.isRegularFile(old.resolve("drinks.yml")));
        assertFalse(Files.exists(plugins.resolve("AniDrinks.migrated")));

        OldInstall.repairRounds(old);
        AniDrinksMigration.Result retry = run();
        assertEquals(1, retry.rounds());
        assertEquals(KASHEYY_ROW, row(KASHEYY), "the retry is not doubled");
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
        assertEquals(KASHEYY_ROW, row(KASHEYY));
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
            assertThrows(SQLException.class, () -> st.executeQuery("SELECT 1 FROM prefs"), "no Prost table was created next to AniDrinks' tables");
        }
    }
}
