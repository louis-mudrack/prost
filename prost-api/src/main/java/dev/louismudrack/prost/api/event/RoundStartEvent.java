package dev.louismudrack.prost.api.event;

import dev.louismudrack.prost.api.RoundView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A round was opened. */
public final class RoundStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final RoundView round;

    /** @param round the round */
    public RoundStartEvent(RoundView round) { this.round = round; }

    /** the round */
    public RoundView round() { return round; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    /** @return the handler list Bukkit requires for every event */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
