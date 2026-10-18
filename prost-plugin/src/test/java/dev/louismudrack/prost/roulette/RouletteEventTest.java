package dev.louismudrack.prost.roulette;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.event.RouletteHitEvent;
import dev.louismudrack.prost.game.Round;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RouletteEventTest {
    private ServerMock server;
    private ProstPlugin plugin;
    private final List<RouletteHitEvent> hits = new ArrayList<>();

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler public void on(RouletteHitEvent e) { hits.add(e); }
        }, plugin);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void aLandedSpinIsReported() {
        PlayerMock p = server.addPlayer("Host");
        p.performCommand("prost start");
        Round r = plugin.rounds().of(p.getUniqueId());
        plugin.roulette().land(r, p.getUniqueId(), 3, "break");
        assertEquals(1, hits.size());
        assertEquals(3, hits.get(0).sips());
        assertEquals(r.code, hits.get(0).roundCode());
    }
}
