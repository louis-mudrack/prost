package dev.louismudrack.prost.api.event;

import dev.louismudrack.prost.api.ChallengeView;
import dev.louismudrack.prost.api.RoundView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A challenge was announced and started. */
public final class ChallengeStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final RoundView round;
    private final ChallengeView challenge;

    /**
     * @param round     the round
     * @param challenge the challenge
     */
    public ChallengeStartEvent(RoundView round, ChallengeView challenge) {
        this.round = round;
        this.challenge = challenge;
    }

    /** the round */
    public RoundView round() { return round; }

    /** the challenge */
    public ChallengeView challenge() { return challenge; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    /** @return the handler list Bukkit requires for every event */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
