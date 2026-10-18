package dev.louismudrack.prost;

/** A real-life table rule (spec §4.5); a player's own rule has the same text in both languages. */
record Rule(String de, String en) {
    String text(boolean german) { return german ? de : en; }
}
