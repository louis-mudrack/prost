package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.SipSource;
import dev.louismudrack.prost.api.event.ChallengeEndEvent;
import dev.louismudrack.prost.api.event.ChallengeStartEvent;
import dev.louismudrack.prost.api.event.RoundEndEvent;
import dev.louismudrack.prost.api.event.RoundStartEvent;
import dev.louismudrack.prost.api.event.SipsEvent;
import dev.louismudrack.prost.drink.SipLedger;
import org.bukkit.event.Event;
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

import static org.junit.jupiter.api.Assertions.*;

class EventsTest {
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host;
    private final List<Event> seen = new ArrayList<>();

    public class Recorder implements Listener {
        @EventHandler public void on(RoundStartEvent e) { seen.add(e); }
        @EventHandler public void on(RoundEndEvent e) { seen.add(e); }
        @EventHandler public void on(ChallengeStartEvent e) { seen.add(e); }
        @EventHandler public void on(ChallengeEndEvent e) { seen.add(e); }
        @EventHandler public void on(SipsEvent e) { seen.add(e); }
    }

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        server.getPluginManager().registerEvents(new Recorder(), plugin);
        host = server.addPlayer("Host");
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private <T extends Event> List<T> of(Class<T> type) {
        return seen.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void aRoundStartsAndEnds() {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        assertInstanceOf(RoundStartEvent.class, seen.get(0));
        assertEquals(r.code, ((RoundStartEvent) seen.get(0)).round().code());
        host.performCommand("prost end");
        assertEquals(1, of(RoundEndEvent.class).size());
        assertEquals(r.code, of(RoundEndEvent.class).get(0).round().code());
    }

    @Test
    void theLastMemberLeavingEndsTheRoundOnce() {
        host.performCommand("prost start");
        host.performCommand("prost leave");
        assertEquals(1, of(RoundEndEvent.class).size());
    }

    @Test
    void aForcedChallengeStarts() {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " walk_km solo");
        ChallengeStartEvent e = of(ChallengeStartEvent.class).get(0);
        assertEquals("walk_km", e.challenge().id());
        assertEquals("solo", e.challenge().kind());
        assertEquals(List.of(host.getUniqueId()), e.challenge().participants());
    }

    @Test
    void aCancelledChallengeEndsUnresolved() {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " walk_km solo");
        plugin.director().running(r).cancel(null);
        ChallengeEndEvent e = of(ChallengeEndEvent.class).get(0);
        assertFalse(e.resolved());
        assertTrue(e.winners().isEmpty());
    }

    @Test
    void aFinishedChallengeEndsResolved() {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " walk_km solo");
        plugin.director().running(r).finish();
        assertEquals(1, of(ChallengeEndEvent.class).size());
        assertTrue(of(ChallengeEndEvent.class).get(0).resolved());
    }

    @Test
    void aRoundEndingMidChallengeFiresOnlyRoundEnd() {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " walk_km solo");
        host.performCommand("prost end");
        server.getScheduler().performTicks(20);
        assertEquals(1, of(RoundEndEvent.class).size());
        assertTrue(of(ChallengeEndEvent.class).isEmpty());
    }

    @Test
    void sipsReportWhatArrived() {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        plugin.sips().drink(r, host.getUniqueId(), 2, SipLedger.Source.CHALLENGE, viewer -> "test");
        SipsEvent e = of(SipsEvent.class).get(0);
        assertEquals(2, e.sips());
        assertEquals(SipSource.CHALLENGE, e.source());
        assertEquals(r.code, e.roundCode());
        assertFalse(e.water());
    }
}
