package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.game.Round;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class ProstExpansionTest {
    private ServerMock server;
    private ProstPlugin plugin;
    private PlaceholderSnapshots snapshots;
    private ProstExpansion expansion;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        snapshots = new PlaceholderSnapshots(plugin);
        expansion = new ProstExpansion(plugin, snapshots);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void roundPlaceholders() {
        PlayerMock p = server.addPlayer("Host");
        snapshots.publish();
        assertEquals("false", expansion.onRequest(p, "in_round"));
        assertEquals("", expansion.onRequest(p, "round_code"));
        p.performCommand("prost start");
        Round r = plugin.rounds().of(p.getUniqueId());
        assertEquals("false", expansion.onRequest(p, "in_round"), "only the published copy is read");
        snapshots.publish();
        assertEquals("true", expansion.onRequest(p, "in_round"));
        assertEquals(r.code, expansion.onRequest(p, "round_code"));
        assertEquals("1", expansion.onRequest(p, "round_players"));
        assertEquals("", expansion.onRequest(p, "challenge"));
        assertEquals("0", expansion.onRequest(p, "sips_session"));
        assertFalse(expansion.onRequest(p, "tag").isEmpty());
        assertNull(expansion.onRequest(p, "nonsense"));
    }

    @Test
    void aPlayerWithoutACopyGetsTheIdleValues() {
        PlayerMock p = server.addPlayer("Host");
        assertEquals("false", expansion.onRequest(p, "in_round"));
        assertEquals("0", expansion.onRequest(p, "round_players"));
        assertEquals("", expansion.onRequest(p, "limit_left"));
        assertEquals("", expansion.onRequest(p, "tag"));
    }

    @Test
    void anEmptyLeaderboard() {
        assertEquals("-", expansion.onRequest(null, "top_1_name"));
        assertEquals("0", expansion.onRequest(null, "top_1_value"));
        assertEquals("-", expansion.onRequest(null, "top_99_name"));
    }

    @Test
    void anUnknownParamIsNullEvenWithoutAPlayer() {
        assertNull(expansion.onRequest(null, "nonsense"));
        assertEquals("", expansion.onRequest(null, "in_round"));
    }

    @Test
    void aQuitEvictsTheCachedTotals() {
        PlayerMock p = server.addPlayer("Host");
        expansion.onRequest(p, "sips_total");
        assertTrue(expansion.cachedTotals() > 0);
        expansion.onQuit(new PlayerQuitEvent(p, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(0, expansion.cachedTotals());
    }

    @Test
    void identity() {
        assertEquals("prost", expansion.getIdentifier());
        assertTrue(expansion.persist());
    }
}
