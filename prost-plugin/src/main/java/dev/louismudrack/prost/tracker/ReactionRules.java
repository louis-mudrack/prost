package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.game.Kind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who drinks after a reaction challenge. Reaction: who is not done drinks; when everybody is done, the last one (an exact
 * tie: all of them). Freeze: who broke it drinks. Solo: done (not broken) wins, else drinks. One penalty per challenge:
 * the winners only get a reward when nobody drinks (Resolution.Outcome#rewarded).
 */
final class ReactionRules {
    private ReactionRules() {}

    static Verdict verdict(Kind kind, boolean freeze, List<UUID> participants, Map<UUID, Long> doneAt, Set<UUID> broken, int sips) {
        Set<UUID> drink = new HashSet<>();
        for (UUID id : participants) if (freeze ? broken.contains(id) : !doneAt.containsKey(id)) drink.add(id);
        // a lone participant left (the others dropped out) is nobody's last
        if (!freeze && drink.isEmpty() && kind == Kind.REACTION && participants.size() > 1) drink.addAll(last(participants, doneAt));
        Map<UUID, Integer> drinks = new LinkedHashMap<>();
        List<UUID> winners = new ArrayList<>();
        for (UUID id : participants) {
            if (drink.contains(id)) drinks.put(id, sips);
            else winners.add(id);
        }
        return Verdict.of(drinks, winners);
    }

    /** the same for both kinds: a Solo has one participant */
    static boolean decided(Kind kind, boolean freeze, List<UUID> participants, Map<UUID, Long> doneAt, Set<UUID> broken) {
        return participants.stream().allMatch(id -> freeze ? broken.contains(id) : doneAt.containsKey(id));
    }

    private static List<UUID> last(List<UUID> participants, Map<UUID, Long> doneAt) {
        long latest = participants.stream().mapToLong(id -> doneAt.get(id)).max().orElseThrow();
        return participants.stream().filter(id -> doneAt.get(id) == latest).toList();
    }
}
