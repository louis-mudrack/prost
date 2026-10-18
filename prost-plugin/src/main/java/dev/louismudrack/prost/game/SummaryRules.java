package dev.louismudrack.prost.game;

import java.util.Collection;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** The round's titles: MVP, Schluckspecht, Gönner - the highest value above 0, a tie goes to who joined first. */
final class SummaryRules {
    private SummaryRules() {}

    static UUID top(Collection<Member> members, ToIntFunction<Member> value) {
        Member best = null;
        for (Member m : members) {
            int v = value.applyAsInt(m);
            if (v <= 0) continue;
            if (best == null || v > value.applyAsInt(best) || (v == value.applyAsInt(best) && m.joinedAt < best.joinedAt)) best = m;
        }
        return best == null ? null : best.id;
    }
}
