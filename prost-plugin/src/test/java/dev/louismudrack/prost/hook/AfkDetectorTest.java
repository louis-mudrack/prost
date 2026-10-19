package dev.louismudrack.prost.hook;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Set;
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

    private static AsyncChatEvent chat(Player p) {
        Component text = Component.text("hi");
        return new AsyncChatEvent(true, p, Set.of(), ChatRenderer.defaultRenderer(), text, text, SignedMessage.system("hi", text));
    }

    @Test
    void chatAndCommandsKeepAPlayerActive() {
        now.set(299_000);
        afk.onChat(chat(player));
        now.set(400_000);
        assertFalse(afk.isAfk(player));
        afk.onCommand(new PlayerCommandPreprocessEvent(player, "/prost"));
        now.set(650_000);
        assertFalse(afk.isAfk(player));
    }

    /** the chat thread can deliver a message after the quit event has removed the player */
    @Test
    void chatOrACommandAfterTheQuitLeavesNoEntry() {
        afk.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        afk.onChat(chat(player));
        afk.onCommand(new PlayerCommandPreprocessEvent(player, "/prost"));
        assertEquals(0, afk.tracked());
    }
}
