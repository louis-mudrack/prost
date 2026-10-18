package dev.louismudrack.prost.catalogue;

/** A real-life table rule (spec §4.5); a player's own rule has the same text in both languages. */
public record Rule(String de, String en) {
    public String text(boolean german) { return german ? de : en; }
}
