package dev.louismudrack.prost.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** A setting changed while the stored ones are still loading is kept; the rest comes from the stored record. */
class PlayerPrefsTest {
    @TempDir Path dir;
    private final ManualWorker worker = new ManualWorker();
    private SqlStatsStore store;
    private PlayerPrefs prefs;
    private PlayerMock player;

    @BeforeEach
    void open() throws Exception {
        player = MockBukkit.mock().addPlayer("Kasheyy");
        store = SqlStatsStore.sqlite(dir.resolve("stats.db").toFile(), "prost_", Logger.getLogger("test"), Runnable::run, worker);
        store.open();
        prefs = new PlayerPrefs(store);
    }

    @AfterEach
    void close() {
        store.close();
        MockBukkit.unmock();
    }

    private void stored(String language, boolean noticed) {
        store.setLanguage(player.getUniqueId(), player.getName(), language);
        if (noticed) store.markNoticed(player.getUniqueId(), player.getName());
    }

    @Test
    void aLanguageChosenWhileLoadingWinsAndTheNoticeIsKept() {
        stored("de", true);
        worker.hold();
        prefs.load(player);
        prefs.setLanguage(player, "en");
        worker.drain();
        assertEquals(Optional.of("en"), prefs.language(player.getUniqueId()));
        assertTrue(prefs.noticed(player.getUniqueId()));
    }

    @Test
    void automaticChosenWhileLoadingIsNotMistakenForUnset() {
        stored("de", true);
        worker.hold();
        prefs.load(player);
        prefs.setLanguage(player, null);
        worker.drain();
        assertEquals(Optional.empty(), prefs.language(player.getUniqueId()));
        assertTrue(prefs.noticed(player.getUniqueId()));
    }

    @Test
    void aNoticeWhileLoadingKeepsTheStoredLanguage() {
        stored("de", false);
        worker.hold();
        prefs.load(player);
        prefs.markNoticed(player);
        worker.drain();
        assertEquals(Optional.of("de"), prefs.language(player.getUniqueId()));
        assertTrue(prefs.noticed(player.getUniqueId()));
    }

    @Test
    void aChangeAfterTheLoadReplacesTheStoredLanguage() {
        stored("de", true);
        prefs.load(player);
        assertEquals(Optional.of("de"), prefs.language(player.getUniqueId()));
        prefs.setLanguage(player, "en");
        assertEquals(Optional.of("en"), prefs.language(player.getUniqueId()));
        assertTrue(prefs.noticed(player.getUniqueId()));
        prefs.setLanguage(player, null);
        assertEquals(Optional.empty(), prefs.language(player.getUniqueId()));
    }

    @Test
    void nothingIsChosenBeforeTheLoad() {
        stored("de", true);
        worker.hold();
        prefs.load(player);
        assertEquals(Optional.empty(), prefs.language(player.getUniqueId()));
        assertFalse(prefs.noticed(player.getUniqueId()));
        worker.drain();
    }
}
