package dev.louismudrack.prost.hook;

import org.bukkit.World;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class HooksTest {
    private PlayerMock player;
    private Plugin other;
    private final List<LogRecord> logged = new ArrayList<>();
    private Hooks hooks;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        player = server.addPlayer("Kasheyy");
        other = MockBukkit.createMockPlugin("Other");
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        log.addHandler(new Handler() {
            @Override public void publish(LogRecord r) { logged.add(r); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        hooks = new Hooks(log, p -> false, env -> env == World.Environment.NORMAL, l -> false);
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    private long warnings() { return logged.stream().filter(r -> r.getLevel() == Level.WARNING).count(); }

    @Test
    void defaultsApplyWithoutProviders() {
        assertTrue(hooks.language(player).isEmpty());
        assertFalse(hooks.afk(player));
        assertFalse(hooks.bot(player));
        assertFalse(hooks.open(World.Environment.NETHER));
        assertFalse(hooks.excluded(player.getLocation()));
        assertFalse(hooks.hasLanguageProvider());
    }

    @Test
    void theNpcMarkerMeansBot() {
        player.setMetadata("NPC", new FixedMetadataValue(other, true));
        assertTrue(hooks.bot(player));
    }

    @Test
    void aProviderReplacesTheDefault() {
        hooks.setAfk(other, p -> true);
        hooks.setLanguage(other, p -> Optional.of("de"));
        hooks.setDimensions(other, env -> true);
        hooks.setAreas(other, l -> true);
        assertTrue(hooks.afk(player));
        assertEquals(Optional.of("de"), hooks.language(player));
        assertTrue(hooks.open(World.Environment.NETHER));
        assertTrue(hooks.excluded(player.getLocation()));
        assertTrue(hooks.hasLanguageProvider());
    }

    @Test
    void disablingTheOwnerDropsItsHooks() {
        hooks.setAfk(other, p -> true);
        hooks.setLanguage(other, p -> Optional.of("de"));
        hooks.onPluginDisable(new PluginDisableEvent(other));
        assertFalse(hooks.afk(player));
        assertFalse(hooks.hasLanguageProvider());
    }

    @Test
    void aThrowingProviderFallsBackAndWarnsOnce() {
        hooks.setAfk(other, p -> { throw new IllegalStateException("boom"); });
        assertFalse(hooks.afk(player));
        assertFalse(hooks.afk(player));
        assertEquals(1, warnings());
    }

    @Test
    void aLinkageErrorIsContainedToo() {
        hooks.setBots(other, p -> { throw new NoClassDefFoundError("dev/example/Missing"); });
        assertFalse(hooks.bot(player));
    }

    @Test
    void aNullLanguageMeansNone() {
        hooks.setLanguage(other, p -> null);
        assertTrue(hooks.language(player).isEmpty());
    }

    @Test
    void aNullAnswerFallsBackToTheDefaultAndWarnsOnce() {
        hooks.setLanguage(other, p -> null);
        hooks.language(player);
        hooks.language(player);
        assertEquals(1, warnings());
    }

    @Test
    void aNewRegistrationReplacesAndDisablingTheReplacedOwnerKeepsTheNewOne() {
        Plugin third = MockBukkit.createMockPlugin("Third");
        hooks.setAfk(other, p -> true);
        hooks.setAfk(third, p -> true);
        hooks.unregisterAll(other);
        assertTrue(hooks.afk(player));
        hooks.unregisterAll(third);
        assertFalse(hooks.afk(player));
    }

    @Test
    void nullArgumentsThrow() {
        assertThrows(NullPointerException.class, () -> hooks.setAfk(null, p -> true));
        assertThrows(NullPointerException.class, () -> hooks.setAfk(other, null));
    }
}
