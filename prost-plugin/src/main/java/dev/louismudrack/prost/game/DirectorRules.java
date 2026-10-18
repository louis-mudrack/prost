package dev.louismudrack.prost.game;

/** When the next challenge may start (spec §3.2, §4.1). One running challenge per round, never two. */
public final class DirectorRules {
    private static final long DAY_TICKS = 24_000L, NIGHT_START = 13_000L, NIGHT_END = 23_000L;

    private DirectorRules() {}

    public static boolean mayStart(boolean running, boolean roundPaused, int active, long now, long nextDrawAt) {
        return !running && !roundPaused && active > 0 && now >= nextDrawAt;
    }

    /** /prost weiter: only between challenges */
    public static boolean mayDrawNow(boolean running) { return !running; }

    public static long afterRun(long now, int rewardSeconds, long gapMs) { return now + rewardSeconds * 1000L + gapMs; }

    public static long afterResume(long now, long nextDrawAt, long minWaitMs) { return Math.max(nextDrawAt, now + minWaitMs); }

    /** overworld time of day 13000-23000 = night (spec §4.4; the time keeps counting across days) */
    public static boolean isNight(long overworldTime) {
        long t = Math.floorMod(overworldTime, DAY_TICKS);
        return t >= NIGHT_START && t < NIGHT_END;
    }
}
