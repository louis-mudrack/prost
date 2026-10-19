package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.reward.Reward;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a game ended: who drinks how many sips (in participant order), who won, and the winners whose reward the game
 * itself decided (granted without a choice menu, still only when nobody drinks).
 */
public record Verdict(Map<UUID, Integer> drinks, List<UUID> winners, Map<UUID, Reward> forced) {
    public Verdict {
        drinks = Collections.unmodifiableMap(new LinkedHashMap<>(drinks));
        winners = List.copyOf(winners);
        forced = Collections.unmodifiableMap(new LinkedHashMap<>(forced));
    }

    public static Verdict of(Map<UUID, Integer> drinks, List<UUID> winners) { return new Verdict(drinks, winners, Map.of()); }
}
