package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class NoticeTest {
    private static final String NOTICE = "Know your limits";

    private ServerMock server;
    private ProstPlugin plugin;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private boolean noticed(PlayerMock p) { return TestServer.messages(p).stream().anyMatch(m -> m.contains(NOTICE)); }

    @Test
    void onlyTheFirstRound() {
        PlayerMock p = server.addPlayer("Host");
        p.performCommand("prost start");
        assertTrue(noticed(p));
        p.performCommand("prost leave");
        TestServer.messages(p).clear();
        p.performCommand("prost start");
        assertNotNull(plugin.rounds().of(p.getUniqueId()));
        assertFalse(noticed(p));
    }

    @Test
    void joiningAlsoCounts() {
        PlayerMock host = server.addPlayer("Host");
        host.performCommand("prost start");
        PlayerMock guest = server.addPlayer("Guest");
        guest.performCommand("prost join " + plugin.rounds().of(host.getUniqueId()).code);
        assertNotNull(plugin.rounds().of(guest.getUniqueId()));
        assertTrue(noticed(guest));
    }

    @Test
    void canBeSwitchedOff() throws Exception {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        c.set("notices.first-round", false);
        c.save(file);
        plugin.reloadData();
        PlayerMock p = server.addPlayer("Fresh");
        p.performCommand("prost start");
        assertFalse(noticed(p));
    }
}
