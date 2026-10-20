package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.game.Kind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Who drinks after a reaction challenge. Reaction: who is not done by the deadline drinks; when everybody is done, the
 * last one (an exact tie: all of them). Freeze: who broke it drinks. Solo: done (not broken) wins, else drinks. One
 * penalty per challenge: the winners only get a reward when nobody drinks (Resolution.Outcome#rewarded).
 */
final class ReactionRules {
    /** when a participant was done; polled: a poll found it, so {@code at} is when the poll looked, not when they did it */
    record Done(long at, boolean polled) {}

    private ReactionRules() {}

    /** {@code deadline}: what was done after it counts as not done - the run's last poll comes on the first tick after it */
    static Verdict verdict(Kind kind, boolean freeze, List<UUID> participants, Map<UUID, Done> done, Set<UUID> broken, long deadline,
                           int sips, Random rnd) {
        Map<UUID, Done> inTime = new LinkedHashMap<>();
        done.forEach((id, d) -> { if (d.at() <= deadline) inTime.put(id, d); });
        Set<UUID> drink = new HashSet<>();
        for (UUID id : participants) if (freeze ? broken.contains(id) : !inTime.containsKey(id)) drink.add(id);
        // a lone participant left (the others dropped out) is nobody's last
        if (!freeze && drink.isEmpty() && kind == Kind.REACTION && participants.size() > 1) drink.addAll(last(participants, inTime, rnd));
        Map<UUID, Integer> drinks = new LinkedHashMap<>();
        List<UUID> winners = new ArrayList<>();
        for (UUID id : participants) {
            if (drink.contains(id)) drinks.put(id, sips);
            else winners.add(id);
        }
        return Verdict.of(drinks, winners);
    }

    /** the same for both kinds: a Solo has one participant */
    static boolean decided(boolean freeze, List<UUID> participants, Map<UUID, Done> done, Set<UUID> broken) {
        return participants.stream().allMatch(id -> freeze ? broken.contains(id) : done.containsKey(id));
    }

    /**
     * Done at the latest moment: by an event, that is a real tie and all of them drink. A poll gives everybody it finds the
     * same time although they did it at different moments since the poll before, so of those only one drinks, at random.
     */
    private static List<UUID> last(List<UUID> participants, Map<UUID, Done> done, Random rnd) {
        long latest = participants.stream().mapToLong(id -> done.get(id).at()).max().orElseThrow();
        List<UUID> out = new ArrayList<>(), polled = new ArrayList<>();
        for (UUID id : participants) {
            Done d = done.get(id);
            if (d.at() == latest) (d.polled() ? polled : out).add(id);
        }
        if (!polled.isEmpty()) out.add(polled.get(rnd.nextInt(polled.size())));
        return out;
    }
}
