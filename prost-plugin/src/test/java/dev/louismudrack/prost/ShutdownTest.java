package dev.louismudrack.prost;

import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.reward.Reward;
import dev.louismudrack.prost.storage.SqlStatsStore;
import dev.louismudrack.prost.storage.StatsRow;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** A server stop in the middle of a round: everything open settles inline, nothing is scheduled, the numbers are stored. */
class ShutdownTest {
    private ServerMock server;
    private ProstPlugin plugin;

    /** a client the server cannot reach any more: hiding its boss bar throws */
    private static final class GoneClient extends PlayerMock {
        GoneClient(ServerMock server, String name) { super(server, name, UUID.nameUUIDFromBytes(name.getBytes())); }

        @Override
        public void hideBossBar(BossBar bar) { throw new IllegalStateException("connection lost"); }
    }

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    /** the scheduler numbers every task: the same gap before and after means nothing was scheduled in between */
    private int nextTaskId(Plugin other) { return server.getScheduler().runTask(other, () -> { }).getTaskId(); }

    private StatsRow stored(UUID id) throws Exception {
        SqlStatsStore reopened = SqlStatsStore.sqlite(new File(plugin.getDataFolder(), "stats.db"),
                plugin.pluginConfig().storage().tablePrefix(), Logger.getLogger("test"), Runnable::run);
        reopened.open();
        try {
            return reopened.fetch(id).get(5, TimeUnit.SECONDS).orElseThrow();
        } finally {
            reopened.close();
        }
    }

    /** a running challenge with its boss bars, an open hand-out (host to guest) and an open joker prompt (guest, 2 sips) */
    private record Open(Round round, PlayerMock host, PlayerMock guest, int handout) {}

    private Open openEverything(PlayerMock host, PlayerMock guest) {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        guest.performCommand("prost join " + r.code);
        assertEquals(2, r.members.size());
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " walk_km solo");
        server.getScheduler().performTicks(10);   // the director's tick shows the boss bars
        assertNotNull(plugin.director().running(r), "a running challenge");

        ChallengeDef handoutOnly = plugin.challenges().stream().filter(d -> d.rewards().equals(List.of(Reward.HANDOUT)) && d.give() > 0)
                .findFirst().orElseThrow();
        plugin.rewards().grant(r, host.getUniqueId(), handoutOnly, false, List.of());
        Member g = r.member(guest.getUniqueId());
        g.jokers = 1;
        plugin.sips().drink(r, guest.getUniqueId(), 2, SipLedger.Source.CHALLENGE, viewer -> "test");
        assertTrue(g.limit >= 2 + handoutOnly.give(), "the test needs room for both");
        assertEquals(0, g.tally.drunk, "the joker prompt and the hand-out are still open");
        return new Open(r, host, guest, handoutOnly.give());
    }

    private void assertSettledAndStored(Open o) throws Exception {
        assertEquals(2 + o.handout(), stored(o.guest().getUniqueId()).drunk(), "the declined joker prompt and the hand-out");
        assertEquals(o.handout(), stored(o.host().getUniqueId()).given());
        String saved = Files.readString(new File(plugin.getDataFolder(), "rounds.yml").toPath());
        assertTrue(saved.contains(o.round().code), "rounds.yml holds the round for the next start");
    }

    @Test
    void aStopMidRoundSchedulesNothingAndStoresTheCounters() throws Exception {
        Open o = openEverything(server.addPlayer("Host"), server.addPlayer("Guest"));
        Plugin other = MockBukkit.createMockPlugin("Other");
        int before = nextTaskId(other);
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(before + 1, nextTaskId(other), "onDisable scheduled a task");
        assertSettledAndStored(o);
    }

    @Test
    void aStepThatThrowsCostsNothingAfterIt() throws Exception {
        GoneClient host = new GoneClient(server, "Host"), guest = new GoneClient(server, "Guest");
        server.addPlayer(host);
        server.addPlayer(guest);
        Open o = openEverything(host, guest);
        server.getPluginManager().disablePlugin(plugin);
        assertSettledAndStored(o);
    }
}
