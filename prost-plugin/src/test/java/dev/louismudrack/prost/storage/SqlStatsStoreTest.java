package dev.louismudrack.prost.storage;

import dev.louismudrack.prost.config.PluginConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static dev.louismudrack.prost.storage.StatsField.*;
import static org.junit.jupiter.api.Assertions.*;

class SqlStatsStoreTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @TempDir Path dir;
    private SqlStatsStore store;

    @BeforeEach
    void open() throws Exception {
        store = SqlStatsStore.sqlite(dir.resolve("stats.db").toFile(), "prost_", Logger.getLogger("test"), Runnable::run);
        store.open();
    }

    @AfterEach
    void close() { store.close(); }

    private <T> T get(Consumer<Consumer<T>> call) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        call.accept(f::complete);
        return f.get(5, TimeUnit.SECONDS);
    }

    private void flush() throws Exception { store.flush().get(5, TimeUnit.SECONDS); }

    @Test
    void countersAddUp() throws Exception {
        store.add(A, "Kasheyy", DRUNK, 3);
        store.add(A, "Kasheyy", DRUNK, 2);
        store.add(A, "Kasheyy", WON, 1);
        StatsRow r = this.<StatsRow>get(c -> store.row(A, c));
        assertEquals(5, r.drunk());
        assertEquals(1, r.won());
        assertEquals("Kasheyy", r.name());
    }

    @Test
    void unknownPlayerHasNoRow() throws Exception { assertNull(this.<StatsRow>get(c -> store.row(B, c))); }

    @Test
    void fetchAnswersEmptyForAnUnknownPlayerAndTheRowOtherwise() throws Exception {
        assertEquals(java.util.Optional.empty(), store.fetch(B).get(5, TimeUnit.SECONDS));
        store.add(A, "Kasheyy", DRUNK, 4);
        assertEquals(4, store.fetch(A).get(5, TimeUnit.SECONDS).orElseThrow().drunk());
    }

    @Test
    void fetchFailsExceptionallyWhenTheDatabaseIsUnreachable() throws Exception {
        java.io.File missing = dir.resolve("never").resolve("down.db").toFile();
        SqlStatsStore down = SqlStatsStore.sqlite(missing, "prost_", Logger.getLogger("test"), Runnable::run, new ManualWorker());
        assertThrows(java.sql.SQLException.class, down::open);
        ExecutionException e = assertThrows(ExecutionException.class, () -> down.fetch(A).get(5, TimeUnit.SECONDS));
        assertInstanceOf(java.sql.SQLException.class, e.getCause());
        down.close();
    }

    @Test
    void fetchFailsExceptionallyOnAClosedStore() throws Exception {
        SqlStatsStore closed = SqlStatsStore.sqlite(dir.resolve("closed.db").toFile(), "prost_", Logger.getLogger("test"), Runnable::run);
        closed.open();
        closed.close();
        ExecutionException e = assertThrows(ExecutionException.class, () -> closed.fetch(A).get(5, TimeUnit.SECONDS));
        assertInstanceOf(java.util.concurrent.RejectedExecutionException.class, e.getCause());
    }

    @Test
    void topIsOrderedAndCachedFor30Seconds() throws Exception {
        store.add(A, "A", DRUNK, 3);
        store.add(B, "B", DRUNK, 9);
        List<StatsRow> top = this.<List<StatsRow>>get(c -> store.top(DRUNK, c));
        assertEquals(List.of(B, A), top.stream().map(StatsRow::id).toList());
        store.add(A, "A", DRUNK, 100);
        flush();
        assertEquals(B, this.<List<StatsRow>>get(c -> store.top(DRUNK, c)).get(0).id(), "served from the cache");
    }

    @Test
    void cachedTopNeverBlocks() throws Exception {
        ManualWorker worker = new ManualWorker();
        SqlStatsStore manual = SqlStatsStore.sqlite(dir.resolve("manual.db").toFile(), "prost_", Logger.getLogger("test"), Runnable::run, worker);
        manual.open();
        worker.hold();
        manual.add(A, "A", DRUNK, 1);
        assertEquals(List.of(), manual.cachedTop(DRUNK), "nothing cached yet: empty now, refreshed in the background");
        worker.drain();
        assertEquals(List.of(A), manual.cachedTop(DRUNK).stream().map(StatsRow::id).toList());
        manual.close();
    }

    @Test
    void prefsRoundTrip() throws Exception {
        assertEquals(Prefs.NONE, this.<Prefs>get(c -> store.prefs(A, c)));
        store.setLanguage(A, "A", "de");
        store.markNoticed(A, "A");
        assertEquals(new Prefs("de", true), this.<Prefs>get(c -> store.prefs(A, c)));
        store.setLanguage(A, "A", null);
        assertNull(this.<Prefs>get(c -> store.prefs(A, c)).language());
    }

    @Test
    void settingsDoNotTouchCounters() throws Exception {
        store.add(A, "A", DRUNK, 4);
        store.setLanguage(A, "A", "de");
        assertEquals(4, this.<StatsRow>get(c -> store.row(A, c)).drunk());
    }

    @Test
    void importIsAbsoluteAndRepeatable() throws Exception {
        StatsRow r = new StatsRow(A, "A", 7, 1, 2, 3, 4, 5, 6, 0, 2);
        store.importRow(r, 123).get(5, TimeUnit.SECONDS);
        store.importRow(r, 123).get(5, TimeUnit.SECONDS);
        assertEquals(r, this.<StatsRow>get(c -> store.row(A, c)));
    }

    @Test
    void aRoundIsStoredOnce() throws Exception {
        store.importRound("L9D5", A.toString(), 10, 20, 2, 16).get(5, TimeUnit.SECONDS);
        store.importRound("L9D5", A.toString(), 10, 20, 2, 16).get(5, TimeUnit.SECONDS);
        store.roundEnded("ABCD", B, 30, 40, 3, 1);
        flush();
        assertEquals(2, store.countRounds());
    }

    @Test
    void hasDataOnceAPlayerIsStored() throws Exception {
        assertFalse(store.hasData().get(5, TimeUnit.SECONDS));
        store.add(A, "A", DRUNK, 1);
        flush();
        assertTrue(store.hasData().get(5, TimeUnit.SECONDS));
    }

    /** a player who only picked a language or saw the notice never played: no row, no top list, no take-over guard */
    @Test
    void settingsAloneAreNoStats() throws Exception {
        store.setLanguage(A, "A", "de");
        store.markNoticed(A, "A");
        flush();
        assertNull(this.<StatsRow>get(c -> store.row(A, c)));
        assertEquals(java.util.Optional.empty(), store.fetch(A).get(5, TimeUnit.SECONDS));
        assertEquals(List.of(), this.<List<StatsRow>>get(c -> store.top(DRUNK, c)));
        assertFalse(store.hasData().get(5, TimeUnit.SECONDS));
        assertEquals(new Prefs("de", true), this.<Prefs>get(c -> store.prefs(A, c)));
    }

    @Test
    void hasDataOnceARoundIsStored() throws Exception {
        store.importRound("L9D5", A.toString(), 10, 20, 2, 16).get(5, TimeUnit.SECONDS);
        assertTrue(store.hasData().get(5, TimeUnit.SECONDS));
    }

    /** an empty table-prefix can meet a table of that name that is not Prost's: AniDrinks' own stats table, another plugin's */
    @Test
    void refusesATableOfItsNameThatIsNotProsts() throws Exception {
        Path shared = dir.resolve("shared.db");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + shared);
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE stats (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, sips_drunk INTEGER NOT NULL DEFAULT 0)");
        }
        SqlStatsStore unprefixed = SqlStatsStore.sqlite(shared.toFile(), "", Logger.getLogger("test"), Runnable::run);
        java.sql.SQLException e = assertThrows(java.sql.SQLException.class, unprefixed::open);
        assertTrue(e.getMessage().contains("table stats is not Prost's") && e.getMessage().contains("storage.table-prefix"), e.getMessage());
        ExecutionException write = assertThrows(ExecutionException.class,
                () -> unprefixed.importRow(new StatsRow(A, "A", 1, 0, 0, 0, 0, 0, 0, 0, 1), 1).get(5, TimeUnit.SECONDS));
        assertInstanceOf(java.sql.SQLException.class, write.getCause());
        unprefixed.close();
    }

    @Test
    void refusesASettingsTableOfItsNameThatIsNotProsts() throws Exception {
        Path shared = dir.resolve("prefs.db");
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + shared);
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE prefs (id INTEGER PRIMARY KEY, value TEXT)");
        }
        SqlStatsStore unprefixed = SqlStatsStore.sqlite(shared.toFile(), "", Logger.getLogger("test"), Runnable::run);
        java.sql.SQLException e = assertThrows(java.sql.SQLException.class, unprefixed::open);
        assertTrue(e.getMessage().contains("table prefs is not Prost's"), e.getMessage());
        unprefixed.close();
    }

    /** a connection cut during the column check stays a lost connection (retried once), not "set a table-prefix" */
    @Test
    void aLostConnectionIsNotMistakenForAForeignTable() {
        java.sql.SQLException lost = new java.sql.SQLException("connection reset", "08S01");
        ClassLoader cl = getClass().getClassLoader();
        java.sql.Statement st = (java.sql.Statement) java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{java.sql.Statement.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("executeQuery")) throw lost;
                    return null;   // close()
                });
        java.sql.Connection c = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{java.sql.Connection.class},
                (proxy, method, args) -> st);
        assertSame(lost, assertThrows(java.sql.SQLException.class, () -> SqlStatsStore.requireOwn(c, "prost_stats", "language, noticed")));
    }

    @Test
    void countersAddedDuringAnOutageAreWrittenWhenTheDatabaseIsBack() throws Exception {
        java.io.File missing = dir.resolve("later").resolve("outage.db").toFile();
        SqlStatsStore outage = SqlStatsStore.sqlite(missing, "prost_", Logger.getLogger("test"), Runnable::run, new ManualWorker());
        assertThrows(java.sql.SQLException.class, outage::open);
        outage.add(A, "old", DRUNK, 3);
        outage.add(A, "new", DRUNK, 2);
        outage.add(A, "new", WON, 1);
        assertTrue(missing.getParentFile().mkdirs());
        outage.add(B, "B", DRUNK, 1);
        StatsRow a = this.<StatsRow>get(c -> outage.row(A, c));
        assertEquals(5, a.drunk());
        assertEquals(1, a.won());
        assertEquals("new", a.name());
        assertEquals(1, this.<StatsRow>get(c -> outage.row(B, c)).drunk());
        outage.close();
    }

    @Test
    void aCorruptRowStillAnswersTheCallback() throws Exception {
        store.add(A, "A", DRUNK, 1);
        flush();
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("stats.db"));
             java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("UPDATE prost_stats SET uuid = 'not-a-uuid'");
        }
        assertEquals(List.of(), this.<List<StatsRow>>get(c -> store.top(DRUNK, c)));
    }

    @Test
    void storageSettingsNeverPrintThePassword() {
        PluginConfig.Storage s = new PluginConfig.Storage("mysql", "h", 3306, "d", "u", "s3cret-pw", "prost_", "");
        assertFalse(s.toString().contains("s3cret-pw"));
        assertTrue(s.toString().contains("password=***"));
    }
}
