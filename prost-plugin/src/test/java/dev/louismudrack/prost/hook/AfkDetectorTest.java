package dev.louismudrack.prost.hook;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AfkDetectorTest {
    private ServerMock server;
    private PlayerMock player;
    private final AtomicLong now = new AtomicLong();
    private final AtomicInteger minutes = new AtomicInteger(5);
    private AfkDetector afk;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        afk = new AfkDetector(minutes::get, now::get);
        afk.onJoin(new PlayerJoinEvent(player, (Component) null));
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void idleForTheLimitIsAfk() {
        now.set(299_999);
        assertFalse(afk.isAfk(player));
        now.set(300_000);
        assertTrue(afk.isAfk(player));
    }

    @Test
    void lookingAroundResetsTheClock() {
        now.set(299_000);
        Location from = player.getLocation(), to = from.clone();
        to.setYaw(from.getYaw() + 10);
        afk.onMove(new PlayerMoveEvent(player, from, to));
        now.set(400_000);
        assertFalse(afk.isAfk(player));
    }

    @Test
    void zeroMinutesMeansNeverAfk() {
        minutes.set(0);
        now.set(99_000_000);
        assertFalse(afk.isAfk(player));
    }

    @Test
    void aPlayerWithoutHistoryIsNotAfk() {
        PlayerMock late = server.addPlayer();
        now.set(10_000_000);
        assertFalse(afk.isAfk(late));
    }
}
