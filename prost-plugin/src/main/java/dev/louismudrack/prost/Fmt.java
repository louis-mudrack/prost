package dev.louismudrack.prost;

/** Small text helpers shared by HUD and chat. */
final class Fmt {
    private Fmt() {}

    /** "m:ss", a started second counts as a full one */
    static String clock(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    /** "1 Schluck" / "3 Schlucke", "1 sip" / "3 sips" */
    static String sips(int n, boolean de) { return n + (de ? (n == 1 ? " Schluck" : " Schlucke") : (n == 1 ? " sip" : " sips")); }
}
