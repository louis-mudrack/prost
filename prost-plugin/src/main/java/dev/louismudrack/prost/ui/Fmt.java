package dev.louismudrack.prost.ui;

/** Small text helpers shared by HUD and chat. */
public final class Fmt {
    private Fmt() {}

    /** "m:ss", a started second counts as a full one */
    public static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
