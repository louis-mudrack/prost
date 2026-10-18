package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.hook.PlaceholderSnapshots.Snapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/** What %prost_*% reads from other threads: a copy the main thread publishes, never the live rounds. */
class PlaceholderSnapshotsTest {
    private ServerMock server;
    private ProstPlugin plugin;
    private PlaceholderSnapshots snapshots;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        snapshots = new PlaceholderSnapshots(plugin);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void aPlayerOutsideARound() {
        PlayerMock p = server.addPlayer("Guest");
        snapshots.publish();
        Snapshot s = snapshots.of(p.getUniqueId());
        assertFalse(s.inRound());
        assertEquals("", s.roundCode());
        assertEquals(0, s.roundPlayers());
        assertEquals("", s.challenge());
        assertEquals("", s.challengeTime());
        assertEquals(0, s.sipsSession());
        assertEquals(0, s.jokers());
        assertEquals("", s.limitLeft());
        assertFalse(s.tag().isEmpty());
    }

    @Test
    void aRoundShowsOnlyAfterTheNextPublish() {
        PlayerMock p = server.addPlayer("Host");
        snapshots.publish();
        p.performCommand("prost start");
        Round r = plugin.rounds().of(p.getUniqueId());
        assertFalse(snapshots.of(p.getUniqueId()).inRound(), "the copy, not the live round");
        snapshots.publish();
        Snapshot s = snapshots.of(p.getUniqueId());
        assertTrue(s.inRound());
        assertEquals(r.code, s.roundCode());
        assertEquals(1, s.roundPlayers());
        assertEquals(0, s.sipsSession());
        assertEquals(String.valueOf(r.member(p.getUniqueId()).limit), s.limitLeft());
    }

    @Test
    void sipsReachTheCopy() {
        PlayerMock p = server.addPlayer("Host");
        p.performCommand("prost start");
        plugin.rounds().of(p.getUniqueId()).member(p.getUniqueId()).tally.drunk = 4;
        snapshots.publish();
        assertEquals(4, snapshots.of(p.getUniqueId()).sipsSession());
    }

    @Test
    void aPlayerWhoLeftIsDropped() {
        PlayerMock p = server.addPlayer("Guest");
        snapshots.publish();
        p.disconnect();
        snapshots.publish();
        assertNull(snapshots.of(p.getUniqueId()));
    }
}
