package dev.louismudrack.prost.storage;

/** What a player chose: their /prost language (null = automatic) and whether they saw the first-round notice. */
public record Prefs(String language, boolean noticed) {
    public static final Prefs NONE = new Prefs(null, false);
}
