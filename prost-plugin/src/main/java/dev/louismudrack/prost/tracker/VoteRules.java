package dev.louismudrack.prost.tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Who drinks after a build contest. Only a vote of a builder for another builder counts; anything else (a vote for
 * yourself, from or for somebody who dropped out) counts as no vote. The fewest votes drink, every builder without a
 * vote drinks one sip more, and the most votes win when that is not also the fewest and they do not drink for not voting.
 * The verdict keeps the builders' order.
 */
final class VoteRules {
    /** what not voting costs on top */
    static final int ABSTAIN_SIPS = 1;

    private VoteRules() {}

    static Verdict verdict(List<UUID> builders, Map<UUID, UUID> votes, int sips) {
        Map<UUID, Integer> counts = counts(builders, votes);
        if (counts.isEmpty()) return Verdict.of(Map.of(), List.of());
        int fewest = Collections.min(counts.values()), most = Collections.max(counts.values());
        Map<UUID, Integer> drinks = new LinkedHashMap<>();
        List<UUID> winners = new ArrayList<>();
        for (UUID id : builders) {
            int n = (counts.get(id) == fewest ? sips : 0) + (voted(votes, builders, id) ? 0 : ABSTAIN_SIPS);
            if (n > 0) drinks.put(id, n);
            if (n == 0 && most != fewest && counts.get(id) == most) winners.add(id);
        }
        return Verdict.of(drinks, winners);
    }

    /** the valid votes each builder got, in the builders' order */
    static Map<UUID, Integer> counts(List<UUID> builders, Map<UUID, UUID> votes) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (UUID id : builders) out.put(id, 0);
        for (UUID voter : builders) if (voted(votes, builders, voter)) out.merge(votes.get(voter), 1, Integer::sum);
        return out;
    }

    /** whom each builder portrays: never themselves, and everybody is portrayed once (a random cycle through all of them) */
    static Map<UUID, UUID> derangement(List<UUID> ids, Random rnd) {
        if (ids.size() < 2) throw new IllegalArgumentException("a derangement needs two ids, got " + ids.size());
        List<UUID> cycle = new ArrayList<>(ids);
        Collections.shuffle(cycle, rnd);
        Map<UUID, UUID> out = new LinkedHashMap<>();
        for (int i = 0; i < cycle.size(); i++) out.put(cycle.get(i), cycle.get((i + 1) % cycle.size()));
        return out;
    }

    /** {@code voter} cast a vote that counts */
    private static boolean voted(Map<UUID, UUID> votes, List<UUID> builders, UUID voter) {
        UUID target = votes.get(voter);
        return target != null && !target.equals(voter) && builders.contains(target);
    }
}
