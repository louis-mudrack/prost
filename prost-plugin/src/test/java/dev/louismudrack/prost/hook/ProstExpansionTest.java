package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.game.Round;
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
    private ProstExpansion expansion;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        expansion = new ProstExpansion(plugin);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void roundPlaceholders() {
        PlayerMock p = server.addPlayer("Host");
        assertEquals("false", expansion.onRequest(p, "in_round"));
        assertEquals("", expansion.onRequest(p, "round_code"));
        p.performCommand("prost start");
        Round r = plugin.rounds().of(p.getUniqueId());
        assertEquals("true", expansion.onRequest(p, "in_round"));
        assertEquals(r.code, expansion.onRequest(p, "round_code"));
        assertEquals("1", expansion.onRequest(p, "round_players"));
        assertEquals("", expansion.onRequest(p, "challenge"));
        assertEquals("0", expansion.onRequest(p, "sips_session"));
        assertFalse(expansion.onRequest(p, "tag").isEmpty());
        assertNull(expansion.onRequest(p, "nonsense"));
    }

    @Test
    void anEmptyLeaderboard() {
        assertEquals("-", expansion.onRequest(null, "top_1_name"));
        assertEquals("0", expansion.onRequest(null, "top_1_value"));
        assertEquals("-", expansion.onRequest(null, "top_99_name"));
    }

    @Test
    void identity() {
        assertEquals("prost", expansion.getIdentifier());
        assertTrue(expansion.persist());
    }
}
