package dev.louismudrack.prost;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The self-test helpers of AniDrinks, kept so the ported checks read the same: check(name, ok), eq(name, want, got). */
public final class Checks {
    public static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    private Checks() {}

    public static void check(String name, boolean ok) { assertTrue(ok, name); }

    public static void eq(String name, Object want, Object got) {
        assertTrue(Objects.equals(want, got), name + " (want " + want + ", got " + got + ")");
    }
}
