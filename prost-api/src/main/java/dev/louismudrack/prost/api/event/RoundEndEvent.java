package dev.louismudrack.prost.api.event;

import dev.louismudrack.prost.api.RoundView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fires whenever a round closes, however it ends (host, admin, last member leaving or removed); the view is its
 * last state. A plugin shutdown does not fire it: rounds are saved and restored, not ended.
 */
public final class RoundEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final RoundView round;

    /** @param round the round's last state */
    public RoundEndEvent(RoundView round) { this.round = round; }

    /** the round's last state */
    public RoundView round() { return round; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    /** @return the handler list Bukkit requires for every event */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
