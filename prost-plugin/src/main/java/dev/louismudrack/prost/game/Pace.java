package dev.louismudrack.prost.game;

import java.util.Locale;

/** How often challenges come (spec §3.2); the windows live in drinks.yml pace.* */
public enum Pace {
    CHILL("chill"), NORMAL("normal"), PARTY("party");

    public final String key;

    Pace(String key) { this.key = key; }

    public static Pace of(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (Pace p : values()) if (p.key.equals(k)) return p;
        return null;
    }

    /** a random gap inside [min, max] seconds */
    public static long gapMs(int[] rangeSeconds, double random) {
        return (rangeSeconds[0] + Math.round(random * (rangeSeconds[1] - rangeSeconds[0]))) * 1000L;
    }
}
