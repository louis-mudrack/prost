package dev.louismudrack.prost.meta;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test
    void noProjectOnModrinthYetIsNotWorthAnInfoLine() {
        assertEquals(Level.FINE, UpdateChecker.failureLevel(404));
    }

    @Test
    void otherFailuresAreTold() {
        assertEquals(Level.INFO, UpdateChecker.failureLevel(500));
        assertEquals(Level.INFO, UpdateChecker.failureLevel(429));
    }
}
