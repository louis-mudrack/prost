package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.tracker.ReactionRules.Done;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReactionRulesTest {
    private static final UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3), d = new UUID(0, 4);
    private static final long DEADLINE = 10_000L;

    private static Done event(long at) { return new Done(at, false); }

    private static Done poll(long at) { return new Done(at, true); }

    private static Verdict verdict(Kind kind, boolean freeze, List<UUID> participants, Map<UUID, Done> done, Set<UUID> broken, int sips) {
        return ReactionRules.verdict(kind, freeze, participants, done, broken, DEADLINE, sips, new Random(1));
    }

    @Test
    void whoIsNotDoneDrinks() {
        Verdict v = verdict(Kind.REACTION, false, List.of(a, b, c), Map.of(a, event(100), b, event(200)), Set.of(), 1);
        assertEquals(Map.of(c, 1), v.drinks());
        assertEquals(List.of(a, b), v.winners());
    }

    @Test
    void everybodyDoneTheLastDrinks() {
        Verdict v = verdict(Kind.REACTION, false, List.of(a, b), Map.of(a, event(100), b, event(300)), Set.of(), 2);
        assertEquals(Map.of(b, 2), v.drinks());
        assertEquals(List.of(a), v.winners());
    }

    @Test
    void anExactTieForLastAllDrink() {
        Verdict v = verdict(Kind.REACTION, false, List.of(a, b, c), Map.of(a, event(100), b, event(300), c, event(300)), Set.of(), 1);
        assertEquals(Map.of(b, 1, c, 1), v.drinks());
        assertEquals(List.of(a), v.winners());
    }

    /** a poll's time is when it looked, not when they did it: of those it found last, one drinks, picked at random */
    @Test
    void aTieForLastFoundByOnePollMakesOneOfThemDrink() {
        Map<UUID, Done> done = Map.of(a, poll(100), b, poll(300), c, poll(300));
        Set<UUID> drinkers = new HashSet<>();
        Random rnd = new Random(3);
        for (int run = 0; run < 20; run++) {
            Verdict v = ReactionRules.verdict(Kind.REACTION, false, List.of(a, b, c), done, Set.of(), DEADLINE, 1, rnd);
            assertEquals(1, v.drinks().size(), "one of them: " + v.drinks());
            assertEquals(1, v.drinks().values().iterator().next());
            assertEquals(2, v.winners().size());
            drinkers.addAll(v.drinks().keySet());
        }
        assertEquals(Set.of(b, c), drinkers, "either of the two, never the first");
    }

    @Test
    void atTheLastMomentEveryEventDrinksAndOneOfThePolled() {
        Map<UUID, Done> done = Map.of(a, event(100), b, event(300), c, poll(300), d, poll(300));
        Verdict v = verdict(Kind.REACTION, false, List.of(a, b, c, d), done, Set.of(), 1);
        assertEquals(2, v.drinks().size(), v.drinks().toString());
        assertTrue(v.drinks().containsKey(b), "done at that very moment");
        assertTrue(v.drinks().containsKey(c) != v.drinks().containsKey(d), "one of the polled: " + v.drinks());
    }

    /** the run ends on the first tick after the deadline: the final poll must not change who drinks */
    @Test
    void onlyWhatWasDoneByTheDeadlineCounts() {
        Verdict late = verdict(Kind.REACTION, false, List.of(a, b, c), Map.of(a, event(100), b, poll(DEADLINE + 400)), Set.of(), 1);
        assertEquals(Map.of(b, 1, c, 1), late.drinks(), "b was too late, as if not done at all");
        assertEquals(List.of(a), late.winners());
        Verdict atTheDeadline = verdict(Kind.REACTION, false, List.of(a, b), Map.of(a, event(100), b, event(DEADLINE)), Set.of(), 1);
        assertEquals(Map.of(b, 1), atTheDeadline.drinks(), "at the deadline is in time: b is the last, not missing");
        Verdict solo = verdict(Kind.SOLO, false, List.of(a), Map.of(a, event(DEADLINE + 1)), Set.of(), 2);
        assertEquals(Map.of(a, 2), solo.drinks(), "a solo too late drinks");
    }

    /** the others dropped out: the one left who did it in time is nobody's "last" */
    @Test
    void aLoneParticipantWhoIsDoneWins() {
        Verdict v = verdict(Kind.REACTION, false, List.of(a), Map.of(a, event(100)), Set.of(), 1);
        assertTrue(v.drinks().isEmpty());
        assertEquals(List.of(a), v.winners());
    }

    @Test
    void freezeBrokenDrinkNobodyBrokeAllWin() {
        assertEquals(Map.of(b, 1), verdict(Kind.REACTION, true, List.of(a, b), Map.of(), Set.of(b), 1).drinks());
        Verdict all = verdict(Kind.REACTION, true, List.of(a, b), Map.of(), Set.of(), 1);
        assertTrue(all.drinks().isEmpty());
        assertEquals(List.of(a, b), all.winners());
    }

    @Test
    void soloDoneWinsElseDrinks() {
        assertEquals(List.of(a), verdict(Kind.SOLO, false, List.of(a), Map.of(a, event(5)), Set.of(), 2).winners());
        assertEquals(Map.of(a, 2), verdict(Kind.SOLO, false, List.of(a), Map.of(), Set.of(), 2).drinks());
        assertEquals(Map.of(a, 1), verdict(Kind.SOLO, true, List.of(a), Map.of(), Set.of(a), 1).drinks());
    }

    @Test
    void winnersKeepTheParticipantOrder() {
        Verdict v = verdict(Kind.REACTION, false, List.of(c, a, b), Map.of(b, event(100), a, event(200)), Set.of(), 1);
        assertEquals(List.of(a, b), v.winners());
        assertTrue(v.forced().isEmpty());
    }

    @Test
    void decidedWhenEverybodyDoneOrEverybodyBroken() {
        assertTrue(ReactionRules.decided(false, List.of(a, b), Map.of(a, event(1), b, event(2)), Set.of()));
        assertFalse(ReactionRules.decided(false, List.of(a, b), Map.of(a, event(1)), Set.of()));
        assertTrue(ReactionRules.decided(true, List.of(a, b), Map.of(), Set.of(a, b)));
        assertFalse(ReactionRules.decided(true, List.of(a, b), Map.of(), Set.of(a)));
        assertTrue(ReactionRules.decided(true, List.of(a), Map.of(), Set.of(a)));
    }
}
