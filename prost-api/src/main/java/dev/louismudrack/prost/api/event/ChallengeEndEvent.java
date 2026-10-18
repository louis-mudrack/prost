package dev.louismudrack.prost.api.event;

import dev.louismudrack.prost.api.ChallengeView;
import dev.louismudrack.prost.api.RoundView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A challenge ended. resolved = false when it was cancelled (nobody drinks, nobody wins). */
public final class ChallengeEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final RoundView round;
    private final ChallengeView challenge;
    private final boolean resolved;
    private final List<UUID> winners;
    private final Map<UUID, Integer> drinks;

    /**
     * @param round     the round
     * @param challenge the challenge
     * @param resolved  false when the challenge was cancelled
     * @param winners   the winners
     * @param drinks    who drinks how many sips
     */
    public ChallengeEndEvent(RoundView round, ChallengeView challenge, boolean resolved, List<UUID> winners, Map<UUID, Integer> drinks) {
        this.round = round;
        this.challenge = challenge;
        this.resolved = resolved;
        this.winners = List.copyOf(winners);
        this.drinks = Collections.unmodifiableMap(new LinkedHashMap<>(drinks));
    }

    /** the round */
    public RoundView round() { return round; }

    /** the challenge */
    public ChallengeView challenge() { return challenge; }

    /** false when the challenge was cancelled */
    public boolean resolved() { return resolved; }

    /** the winners */
    public List<UUID> winners() { return winners; }

    /** who drinks how many sips (before jokers and limits) */
    public Map<UUID, Integer> drinks() { return drinks; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    /** @return the handler list Bukkit requires for every event */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
