package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.reward.Reward;
import dev.louismudrack.prost.tracker.LuckRules.Prize;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LuckRulesTest {
    private static final UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3);

    @Test
    void wheelLowestDrinkTiesAll() {
        Verdict v = LuckRules.wheel(Kind.LUCK, Map.of(a, 7, b, 3, c, 3), 2);
        assertEquals(Map.of(b, 2, c, 2), v.drinks());
        assertEquals(List.of(a), v.winners());
    }

    @Test
    void wheelSoloThreeOrLessDrinks() {
        assertEquals(Map.of(a, 2), LuckRules.wheel(Kind.SOLO, Map.of(a, 3), 2).drinks());
        assertEquals(List.of(a), LuckRules.wheel(Kind.SOLO, Map.of(a, 4), 2).winners());
        assertTrue(LuckRules.wheel(Kind.SOLO, Map.of(a, 4), 2).drinks().isEmpty());
    }

    /** the others dropped out: the lowest of one would always drink, so the lone spinner plays the solo wheel */
    @Test
    void aLoneSpinnerPlaysTheSoloWheel() {
        assertEquals(List.of(a), LuckRules.wheel(Kind.LUCK, Map.of(a, 5), 2).winners());
        assertEquals(Map.of(a, 2), LuckRules.wheel(Kind.LUCK, Map.of(a, 2), 2).drinks());
    }

    @Test
    void coinWrongCallerDrinksRightOtherDrinks() {
        assertEquals(Map.of(a, 2), LuckRules.coin(a, b, true, false, 2).drinks());
        assertEquals(List.of(b), LuckRules.coin(a, b, true, false, 2).winners());
        assertEquals(Map.of(b, 2), LuckRules.coin(a, b, true, true, 2).drinks());
        assertEquals(List.of(a), LuckRules.coin(a, b, false, false, 2).winners());
    }

    @Test
    void chestsForceTheirPrize() {
        assertEquals(Map.of(a, Reward.JOKER), LuckRules.chests(a, Prize.JOKER, 3).forced());
        assertEquals(List.of(a), LuckRules.chests(a, Prize.JOKER, 3).winners());
        assertEquals(Map.of(a, Reward.HANDOUT), LuckRules.chests(a, Prize.HANDOUT, 3).forced());
        assertEquals(Map.of(a, 3), LuckRules.chests(a, Prize.DRINK, 3).drinks());
        assertTrue(LuckRules.chests(a, Prize.DRINK, 3).winners().isEmpty());
    }

    @Test
    void diceMiddleIsNothing() {
        Verdict v = LuckRules.dice(a, 4, 2);
        assertTrue(v.drinks().isEmpty());
        assertTrue(v.winners().isEmpty());
        assertTrue(LuckRules.dice(a, 3, 2).winners().isEmpty());
        assertEquals(Map.of(a, Reward.HANDOUT), LuckRules.dice(a, 6, 2).forced());
        assertEquals(Map.of(a, Reward.HANDOUT), LuckRules.dice(a, 5, 2).forced());
        assertEquals(Map.of(a, 2), LuckRules.dice(a, 1, 2).drinks());
        assertEquals(Map.of(a, 2), LuckRules.dice(a, 2, 2).drinks());
    }

    @Test
    void highLowWrongDrinksBothRightWin() {
        Verdict v = LuckRules.highLow(Map.of(a, true, b, false), 5, 8, 2);
        assertEquals(Map.of(b, 2), v.drinks());
        assertEquals(List.of(a), v.winners());
        Verdict both = LuckRules.highLow(Map.of(a, true, b, true), 5, 8, 2);
        assertTrue(both.drinks().isEmpty());
        assertEquals(2, both.winners().size());
        assertEquals(Map.of(a, 2), LuckRules.highLow(Map.of(a, true), 5, 2, 2).drinks(), "lower than shown: higher was wrong");
    }

    @Test
    void boxesTrapPickerDrinks() {
        Verdict v = LuckRules.boxes(Map.of(a, 0, b, 1, c, 2), 1, 3);
        assertEquals(Map.of(b, 3), v.drinks());
        assertEquals(2, v.winners().size());
        assertFalse(v.winners().contains(b));
        assertTrue(LuckRules.boxes(Map.of(a, 0, c, 2), 1, 3).drinks().isEmpty(), "the trap's taker dropped out: nobody drinks");
    }

    @Test
    void theVerdictKeepsTheParticipantOrder() {
        Map<UUID, Integer> rolls = new LinkedHashMap<>();
        rolls.put(c, 9);
        rolls.put(a, 1);
        rolls.put(b, 8);
        assertEquals(List.of(c, b), LuckRules.wheel(Kind.LUCK, rolls, 2).winners());
    }

    @Test
    void nextNumberNeverEqualsAndStaysInRange() {
        Random rnd = new Random(42);
        for (int shown = 1; shown <= 10; shown++) {
            boolean[] seen = new boolean[11];
            for (int i = 0; i < 10_000; i++) {
                int n = LuckRules.nextNumber(shown, rnd);
                assertTrue(n >= 1 && n <= 10, shown + " -> " + n);
                assertNotEquals(shown, n);
                seen[n] = true;
            }
            for (int n = 1; n <= 10; n++) assertEquals(n != shown, seen[n], "shown " + shown + ": " + n + " can come");
        }
    }
}
