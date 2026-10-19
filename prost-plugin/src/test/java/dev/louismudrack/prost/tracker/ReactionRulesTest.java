package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.game.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReactionRulesTest {
    private static final UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);

    @Test
    void whoIsNotDoneDrinks() {
        Verdict v = ReactionRules.verdict(Kind.REACTION, false, List.of(a, b, c), Map.of(a, 100L, b, 200L), Set.of(), 1);
        assertEquals(Map.of(c, 1), v.drinks());
        assertEquals(List.of(a, b), v.winners());
    }

    @Test
    void everybodyDoneTheLastDrinks() {
        Verdict v = ReactionRules.verdict(Kind.REACTION, false, List.of(a, b), Map.of(a, 100L, b, 300L), Set.of(), 2);
        assertEquals(Map.of(b, 2), v.drinks());
        assertEquals(List.of(a), v.winners());
    }

    @Test
    void anExactTieForLastAllDrink() {
        Verdict v = ReactionRules.verdict(Kind.REACTION, false, List.of(a, b, c), Map.of(a, 100L, b, 300L, c, 300L), Set.of(), 1);
        assertEquals(Map.of(b, 1, c, 1), v.drinks());
        assertEquals(List.of(a), v.winners());
    }

    /** the others dropped out: the one left who did it in time is nobody's "last" */
    @Test
    void aLoneParticipantWhoIsDoneWins() {
        Verdict v = ReactionRules.verdict(Kind.REACTION, false, List.of(a), Map.of(a, 100L), Set.of(), 1);
        assertTrue(v.drinks().isEmpty());
        assertEquals(List.of(a), v.winners());
    }

    @Test
    void freezeBrokenDrinkNobodyBrokeAllWin() {
        assertEquals(Map.of(b, 1), ReactionRules.verdict(Kind.REACTION, true, List.of(a, b), Map.of(), Set.of(b), 1).drinks());
        Verdict all = ReactionRules.verdict(Kind.REACTION, true, List.of(a, b), Map.of(), Set.of(), 1);
        assertTrue(all.drinks().isEmpty());
        assertEquals(List.of(a, b), all.winners());
    }

    @Test
    void soloDoneWinsElseDrinks() {
        assertEquals(List.of(a), ReactionRules.verdict(Kind.SOLO, false, List.of(a), Map.of(a, 5L), Set.of(), 2).winners());
        assertEquals(Map.of(a, 2), ReactionRules.verdict(Kind.SOLO, false, List.of(a), Map.of(), Set.of(), 2).drinks());
        assertEquals(Map.of(a, 1), ReactionRules.verdict(Kind.SOLO, true, List.of(a), Map.of(), Set.of(a), 1).drinks());
    }

    @Test
    void winnersKeepTheParticipantOrder() {
        Verdict v = ReactionRules.verdict(Kind.REACTION, false, List.of(c, a, b), Map.of(b, 100L, a, 200L), Set.of(), 1);
        assertEquals(List.of(a, b), v.winners());
        assertTrue(v.forced().isEmpty());
    }

    @Test
    void decidedWhenEverybodyDoneOrEverybodyBroken() {
        assertTrue(ReactionRules.decided(Kind.REACTION, false, List.of(a, b), Map.of(a, 1L, b, 2L), Set.of()));
        assertFalse(ReactionRules.decided(Kind.REACTION, false, List.of(a, b), Map.of(a, 1L), Set.of()));
        assertTrue(ReactionRules.decided(Kind.REACTION, true, List.of(a, b), Map.of(), Set.of(a, b)));
        assertFalse(ReactionRules.decided(Kind.REACTION, true, List.of(a, b), Map.of(), Set.of(a)));
        assertTrue(ReactionRules.decided(Kind.SOLO, true, List.of(a), Map.of(), Set.of(a)));
    }
}
