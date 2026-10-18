package dev.louismudrack.prost.hook;

import java.util.UUID;

/** The language until the i18n system exists (Task 8): English for everyone. */
public final class Gate {
    private Gate() {}

    public static String lang(UUID id) { return "en"; }
}
