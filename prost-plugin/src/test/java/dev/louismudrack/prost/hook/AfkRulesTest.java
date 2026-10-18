package dev.louismudrack.prost.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AfkRulesTest {
    @Test
    void lookingAroundCounts() { assertTrue(AfkRules.activity(0, 0, 5f, 0f, false, true)); }

    @Test
    void walkingOnTheGroundCounts() { assertTrue(AfkRules.activity(0.2, 0, 0f, 0f, true, false)); }

    @Test
    void beingPushedDoesNot() {
        assertFalse(AfkRules.activity(0.5, 0.5, 0f, 0f, false, false), "falling or swimming in a stream");
        assertFalse(AfkRules.activity(0.5, 0.5, 0f, 0f, true, true), "riding a minecart");
    }

    @Test
    void theLimitAndZero() {
        assertFalse(AfkRules.isAfk(0, 120_000, 5));
        assertTrue(AfkRules.isAfk(0, 300_000, 5));
        assertFalse(AfkRules.isAfk(0, 99_000_000, 0));
    }
}
