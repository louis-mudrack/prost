package dev.louismudrack.prost.game;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.event.ChallengeEndEvent;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.menu.ChoiceMenu;
import dev.louismudrack.prost.menu.HandoutMenu;
import dev.louismudrack.prost.reward.Reward;
import dev.louismudrack.prost.tracker.GameTracker;
import dev.louismudrack.prost.tracker.Verdict;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The run's side of a game tracker: deadline, cancel, drop, verdict, stats and a hidden clock, with a scripted game. */
class GameRunTest {
    private static final long MINUTE_MS = 60_000L;

    /** a game whose every answer the test sets; it listens while it runs, as the real games do */
    static final class ScriptedGame implements GameTracker, Listener {
        long deadline;
        boolean decided, hidden, stopped;
        String cancelKey, result;
        Verdict verdict = Verdict.of(Map.of(), List.of());
        /** non-null: verdict() throws it (a RuntimeException or an Error) */
        Throwable verdictFails;
        /** non-null: start() throws it once it listens */
        Throwable startFails;
        /** non-null: stop() throws it once it stopped listening */
        RuntimeException stopFails;
        final List<UUID> dropped = new ArrayList<>();
        int verdicts;

        @Override public void start(List<Player> players) {
            Bukkit.getPluginManager().registerEvents(this, Bukkit.getPluginManager().getPlugin("Prost"));
            if (startFails != null) throwUnchecked(startFails);
        }
        @Override public int progress(UUID id) { return 0; }
        @Override public void stop() {
            HandlerList.unregisterAll(this);
            stopped = true;
            if (stopFails != null) throw stopFails;
        }
        @Override public long deadline() { return deadline; }
        @Override public boolean decided() { return decided; }
        @Override public Verdict verdict() {
            verdicts++;
            if (verdictFails != null) throwUnchecked(verdictFails);
            return verdict;
        }
        @Override public String status(UUID viewer, UUID who) { return "<white>playing"; }
        @Override public String result(UUID viewer) { return result; }
        @Override public boolean hiddenClock() { return hidden; }
        @Override public void dropped(UUID id) { dropped.add(id); }
        @Override public String cancelKey() { return cancelKey; }

        @EventHandler public void onQuit(PlayerQuitEvent e) { }

        private static void throwUnchecked(Throwable t) {
            if (t instanceof RuntimeException e) throw e;
            throw (Error) t;
        }
    }

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<ChallengeEndEvent> ended = new ArrayList<>();
    private final ScriptedGame game = new ScriptedGame();
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host, anna, ben;
    private Round round;

    public class Recorder implements Listener {
        @EventHandler public void on(ChallengeEndEvent e) { ended.add(e); }
    }

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        plugin.clock(() -> now.get());
        server.getPluginManager().registerEvents(new Recorder(), plugin);
        host = server.addPlayer("Host");
        anna = server.addPlayer("Anna");
        ben = server.addPlayer("Ben");
        host.performCommand("prost start");
        round = plugin.rounds().of(host.getUniqueId());
        anna.performCommand("prost join " + round.code);
        ben.performCommand("prost join " + round.code);
        game.deadline = now.get() + MINUTE_MS;
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private static ChallengeDef def() {
        return new ChallengeDef("scripted", Map.of("en", "Scripted"), Map.of("en", "Play along"), "scripted", "", "", "", "", List.of(),
                1, 60, 2, 2, List.of(Kind.REACTION, Kind.DUEL), List.of(Reward.HANDOUT), Set.of(), List.of(), 1, 1);
    }

    private ChallengeRun run(Kind kind, PlayerMock... players) {
        List<UUID> ids = new ArrayList<>();
        for (PlayerMock p : players) ids.add(p.getUniqueId());
        ChallengeRun run = new ChallengeRun(plugin, round, def(), kind, ids, ctx -> game);
        run.start();
        return run;
    }

    private Tally tally(PlayerMock p) { return round.member(p.getUniqueId()).tally; }

    private static String barName(PlayerMock p) {
        List<BossBar> bars = new ArrayList<>(p.getBossBars());
        assertEquals(1, bars.size(), p.getName() + "'s bars");
        return PlainTextComponentSerializer.plainText().serialize(bars.get(0).name());
    }

    private static UUID id(PlayerMock p) { return p.getUniqueId(); }

    @Test
    void theRunEndsAtTheGamesDeadlineAlsoWhenItMoves() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        assertEquals(game.deadline, run.endsAt(), "the game's deadline from the start");
        game.deadline += MINUTE_MS;
        now.addAndGet(MINUTE_MS);
        run.tick();
        assertEquals(game.deadline, run.endsAt(), "moved by the game");
        assertTrue(ended.isEmpty(), "the old deadline has passed, the new one has not");
        now.set(game.deadline);
        run.tick();
        assertEquals(1, ended.size());
        assertTrue(ended.get(0).resolved());
        assertEquals(1, game.verdicts, "the verdict is asked once");
    }

    @Test
    void decidedEndsTheRunBeforeTheDeadline() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        game.decided = true;
        run.tick();
        assertEquals(1, ended.size());
        assertTrue(game.stopped);
    }

    @Test
    void aCancelKeyCancelsTheRunAndNobodyDrinks() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        game.verdict = Verdict.of(Map.of(id(host), 2), List.of(id(anna), id(ben)));
        game.cancelKey = "challenge.gate_closed";
        TestServer.messages(host);
        run.tick();
        assertEquals(1, ended.size());
        assertFalse(ended.get(0).resolved(), "cancelled, not resolved");
        assertTrue(ended.get(0).drinks().isEmpty());
        assertTrue(ended.get(0).winners().isEmpty());
        assertEquals(0, game.verdicts, "a cancelled game is not judged");
        assertTrue(game.stopped);
        assertEquals(0, tally(host).drunk);
        assertEquals(0, tally(anna).won);
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("cancelled, nobody drinks")), "the key is told");
        assertTrue(host.getBossBars().isEmpty(), "the bars are gone");
    }

    @Test
    void aDropMidGameReachesTheGame() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        round.member(id(anna)).selfPaused = true;
        run.tick();
        assertEquals(List.of(id(anna)), game.dropped);
        assertEquals(List.of(id(host), id(ben)), run.participants());
        assertTrue(ended.isEmpty(), "the others play on");
    }

    @Test
    void aDuelGameWalkoverSkipsTheVerdict() {
        ChallengeRun run = run(Kind.DUEL, host, anna);
        game.verdict = Verdict.of(Map.of(id(host), 2), List.of(id(anna)));
        round.member(id(anna)).selfPaused = true;
        run.tick();
        assertEquals(List.of(id(anna)), game.dropped);
        assertEquals(0, game.verdicts);
        assertEquals(List.of(id(host)), ended.get(0).winners());
        assertTrue(ended.get(0).drinks().isEmpty());
    }

    @Test
    void whoNeitherWonNorDrinksCountsAsNeither() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        game.verdict = Verdict.of(Map.of(id(anna), 2), List.of(id(host)));   // Ben neither won nor drinks
        TestServer.messages(host);
        run.finish();
        assertEquals(Map.of(id(anna), 2), ended.get(0).drinks());
        assertEquals(List.of(id(host)), ended.get(0).winners());
        assertEquals(1, tally(host).won);
        assertEquals(0, tally(host).lost);
        assertEquals(0, tally(anna).won);
        assertEquals(1, tally(anna).lost);
        assertEquals(0, tally(ben).won, "neither");
        assertEquals(0, tally(ben).lost, "neither");
        List<String> told = TestServer.messages(host);
        assertTrue(told.stream().anyMatch(m -> m.contains("Made it: Host (no reward, someone drinks)")), told.toString());
    }

    private static InventoryHolder holder(PlayerMock p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder(false);
    }

    /** the entry's own reward (hand out) would open the hand-out menu: the game decided a joker instead */
    @Test
    void aForcedRewardIsGivenAsItIsWithoutAMenu() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        game.verdict = new Verdict(Map.of(), List.of(id(host), id(anna)), Map.of(id(host), Reward.JOKER));
        run.finish();
        assertEquals(1, round.member(id(host)).jokers, "the forced joker");
        assertFalse(holder(host) instanceof HandoutMenu || holder(host) instanceof ChoiceMenu, "no menu for a forced reward");
        assertInstanceOf(HandoutMenu.class, holder(anna), "a winner without a forced reward gets the entry's reward");
    }

    @Test
    void aForcedRewardFollowsTheOnePenaltyRule() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        game.verdict = new Verdict(Map.of(id(ben), 2), List.of(id(host)), Map.of(id(host), Reward.JOKER));
        run.finish();
        assertEquals(0, round.member(id(host)).jokers, "somebody drinks: no reward on top");
        assertEquals(Map.of(id(ben), 2), ended.get(0).drinks());
    }

    @Test
    void aGameResultReplacesTheDefaultLine() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        game.verdict = Verdict.of(Map.of(id(anna), 2), List.of(id(host)));
        game.result = "<hl>Anna rolled a 1";
        TestServer.messages(host);
        run.finish();
        List<String> told = TestServer.messages(host);
        assertTrue(told.stream().anyMatch(m -> m.contains("Anna rolled a 1")), told.toString());
        assertTrue(told.stream().noneMatch(m -> m.contains("Made it:")), told.toString());
    }

    @Test
    void aHiddenClockShowsQuestionMarksNotTheTime() {
        game.hidden = true;
        game.deadline = now.get() + 90_000L;
        TestServer.messages(host);
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        assertEquals("??", run.clock());
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Time: ??")), "the start message hides it too");
        now.addAndGet(30_000L);
        run.tick();
        String bar = barName(host);
        assertTrue(bar.endsWith("??"), bar);
        assertFalse(bar.contains("1:00") || bar.contains("1:30"), bar);
        assertEquals(1f, new ArrayList<>(host.getBossBars()).get(0).progress(), "a full bar does not tell the time either");
        assertEquals("??", run.clock());
    }

    private boolean listens(Listener l) { return HandlerList.getRegisteredListeners(plugin).stream().anyMatch(r -> r.getListener() == l); }

    /** a throw while judging must not hold the round: it is cancelled as an error and the next challenge comes */
    @Test
    void aGameWhoseVerdictFailsFreesTheRound() {
        game.verdictFails = new IllegalStateException("scripted verdict failure");
        List<UUID> ids = List.of(id(host), id(anna), id(ben));
        plugin.director().launch(new ChallengeRun(plugin, round, def(), Kind.REACTION, ids, ctx -> game));
        assertTrue(listens(game), "the game listens while it runs");
        game.decided = true;
        TestServer.messages(host);
        try (LogCapture log = LogCapture.root()) {
            plugin.director().tick();
            assertEquals(1, log.records().stream().filter(r -> r.getThrown() == game.verdictFails).count(), "logged once: " + log.messages());
        }
        assertNull(plugin.director().running(round), "the broken run is gone");
        assertFalse(listens(game), "no listener left");
        assertTrue(host.getBossBars().isEmpty(), "no bar left");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("hit an error")), "the round is told");
        assertEquals(1, ended.size(), "API listeners see it end");
        assertFalse(ended.get(0).resolved());
        assertTrue(ended.get(0).drinks().isEmpty());
        assertTrue(plugin.director().next(round), "the next challenge may be drawn");
        plugin.director().tick();
        assertNotNull(plugin.director().running(round), "and is drawn");
    }

    /** a linkage error (a missing class of an optional hook) is no different from any other error: the run is cancelled */
    @Test
    void aGameThatThrowsALinkageErrorWhileRunningIsCancelled() {
        game.verdictFails = new NoClassDefFoundError("scripted/Missing");
        launch(round, game);
        game.decided = true;
        TestServer.messages(host);
        try (LogCapture log = LogCapture.root()) {
            plugin.director().tick();
            assertEquals(1, log.records().stream().filter(r -> r.getThrown() == game.verdictFails).count(), "logged once: " + log.messages());
        }
        assertNull(plugin.director().running(round), "the broken run is gone");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("hit an error")), "the round is told");
        assertTrue(plugin.director().next(round), "the next challenge may be drawn");
    }

    /** a second round of two, hosted by Dora */
    private Round secondRound() {
        PlayerMock dora = server.addPlayer("Dora"), eve = server.addPlayer("Eve");
        dora.performCommand("prost start");
        Round second = plugin.rounds().of(dora.getUniqueId());
        eve.performCommand("prost join " + second.code);
        return second;
    }

    /** another scripted game with the first one's minute to play */
    private ScriptedGame otherGame() {
        ScriptedGame other = new ScriptedGame();
        other.deadline = game.deadline;
        return other;
    }

    private void launch(Round r, ScriptedGame g) {
        plugin.director().launch(new ChallengeRun(plugin, r, def(), Kind.REACTION, r.activeIds(), ctx -> g));
    }

    /** a run that throws while it stops for its ended round must not stop the loop: the other rounds play on */
    @Test
    void aRunWhoseStopFailsDoesNotBreakTheLoop() {
        game.stopFails = new IllegalStateException("scripted stop failure");
        launch(round, game);
        Round second = secondRound();
        ScriptedGame other = otherGame();
        launch(second, other);
        other.decided = true;
        host.performCommand("prost end");
        assertDoesNotThrow(() -> plugin.director().tick());
        assertNull(plugin.director().running(round), "the ended round's run is gone");
        assertEquals(1, other.verdicts, "the other round's game was judged");
    }

    @Test
    void aShutdownStopsEveryRunEvenWhenOneFails() {
        game.stopFails = new IllegalStateException("scripted stop failure");
        launch(round, game);
        ScriptedGame other = otherGame();
        launch(secondRound(), other);
        try (LogCapture log = LogCapture.root()) {
            assertDoesNotThrow(() -> plugin.director().shutdown());
            List<String> warned = log.records().stream().filter(r -> r.getThrown() == game.stopFails).map(LogCapture::text).toList();
            assertEquals(1, warned.size(), "logged once: " + log.messages());
            assertTrue(warned.get(0).contains("scripted") && warned.get(0).contains(round.code), "names the challenge and the round: " + warned);
        }
        assertTrue(game.stopped, "the failing game was stopped");
        assertTrue(other.stopped, "and the other one too");
    }

    /** a throw while starting must not hold the round either: it is cancelled as an error and the next challenge may come */
    @Test
    void aGameWhoseStartFailsFreesTheRound() {
        game.startFails = new IllegalStateException("scripted start failure");
        TestServer.messages(host);
        try (LogCapture log = LogCapture.root()) {
            launch(round, game);
            assertEquals(1, log.records().stream().filter(r -> r.getThrown() == game.startFails).count(), "logged once: " + log.messages());
        }
        assertNull(plugin.director().running(round), "the broken run is gone");
        assertFalse(listens(game), "no listener left");
        assertTrue(host.getBossBars().isEmpty(), "no bar left");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("hit an error")), "the round is told");
        assertEquals(1, ended.size(), "API listeners see it end");
        assertFalse(ended.get(0).resolved());
        assertTrue(plugin.director().next(round), "the next challenge may be drawn");
    }

    @Test
    void aGameWhoseStartThrowsALinkageErrorFreesTheRound() {
        game.startFails = new NoClassDefFoundError("scripted/Missing");
        TestServer.messages(host);
        try (LogCapture log = LogCapture.root()) {
            launch(round, game);
            assertEquals(1, log.records().stream().filter(r -> r.getThrown() == game.startFails).count(), "logged once: " + log.messages());
        }
        assertNull(plugin.director().running(round), "the broken run is gone");
        assertFalse(listens(game), "no listener left");
        assertTrue(host.getBossBars().isEmpty(), "no bar left");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("hit an error")), "the round is told");
        assertEquals(1, ended.size(), "API listeners see it end");
        assertFalse(ended.get(0).resolved());
        assertTrue(plugin.director().next(round), "the next challenge may be drawn");
    }

    /** the hot potato draws its hidden length when it starts: a random source that throws breaks that start */
    @Test
    void aForcedChallengeWhoseStartFailsLeavesNoRun() {
        IllegalStateException broken = new IllegalStateException("scripted random failure");
        plugin.random(new Random() {
            @Override public long nextLong(long origin, long bound) { throw broken; }
        });
        int played = round.challenges;
        TestServer.messages(server.getConsoleSender());
        try (LogCapture log = LogCapture.root()) {
            server.dispatchCommand(server.getConsoleSender(), "prost admin force " + round.code + " hot_potato reaction");
            assertEquals(1, log.records().stream().filter(r -> r.getThrown() == broken).count(), "the start failed: " + log.messages());
        }
        assertTrue(TestServer.messages(server.getConsoleSender()).stream().anyMatch(m -> m.contains("failed to start")), "force says so");
        assertEquals(played, round.challenges, "a failed start does not count as played");
        assertTrue(round.used.contains("hot_potato"), "and is not drawn again before the pool resets");
        assertNull(plugin.director().running(round), "no run left");
        plugin.random(new Random());
        assertTrue(plugin.director().next(round), "the round may draw again");
        plugin.director().tick();
        assertNotNull(plugin.director().running(round), "and does");
    }

    @Test
    void aShownClockCountsDown() {
        ChallengeRun run = run(Kind.REACTION, host, anna, ben);
        now.addAndGet(15_000L);
        run.tick();
        assertEquals("0:45", run.clock());
        assertTrue(barName(host).endsWith("0:45"), barName(host));
    }
}
