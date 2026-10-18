package dev.louismudrack.prost.drink;

import java.util.List;

/**
 * What actually reaches a member: jokers cover challenge penalties and roulette hits, never handed-out sips;
 * a member's own limit counts every sip of the last rolling hour, the rest lapses.
 */
public final class SipLedger {
    public enum Source { CHALLENGE, ROULETTE, HANDOUT }

    public record Sip(long at, int amount) {}

    record Applied(int drunk, int lapsed) {}

    static final long HOUR_MS = 3_600_000L;

    private SipLedger() {}

    public static int recent(List<Sip> history, long now) {
        int sum = 0;
        for (Sip s : history) if (now - s.at() < HOUR_MS) sum += s.amount();
        return sum;
    }

    public static int room(List<Sip> history, long now, int limit) { return Math.max(0, limit - recent(history, now)); }

    /** records what is drunk now and drops entries older than an hour */
    static Applied apply(List<Sip> history, long now, int amount, int limit) {
        history.removeIf(s -> now - s.at() >= HOUR_MS);
        int wanted = Math.max(0, amount);
        int drunk = Math.min(wanted, room(history, now, limit));
        if (drunk > 0) history.add(new Sip(now, drunk));
        return new Applied(drunk, wanted - drunk);
    }

    /** a joker prompt makes sense only for a member who is still here, has one, and for what it covers */
    static boolean offerJoker(int jokers, Source source, boolean online) {
        return online && jokers > 0 && source != Source.HANDOUT;
    }
}
