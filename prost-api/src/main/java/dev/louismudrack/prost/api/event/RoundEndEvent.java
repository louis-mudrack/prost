package dev.louismudrack.prost.api.event;

import dev.louismudrack.prost.api.RoundView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** A round ended (host, admin, or its last member left); the view is its last state. */
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
