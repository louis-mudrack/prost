package dev.louismudrack.prost.api.event;

import dev.louismudrack.prost.api.SipSource;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Sips that actually reached a player (after jokers and the hourly limit). water = the player plays with water. */
public final class SipsEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final String roundCode;
    private final int sips;
    private final SipSource source;
    private final boolean water;

    /**
     * @param player    the player who drinks
     * @param roundCode the round's join code
     * @param sips      the sips
     * @param source    where the sips came from
     * @param water     whether the player plays with water
     */
    public SipsEvent(UUID player, String roundCode, int sips, SipSource source, boolean water) {
        this.player = player;
        this.roundCode = roundCode;
        this.sips = sips;
        this.source = source;
        this.water = water;
    }

    /** the player who drinks */
    public UUID player() { return player; }

    /** the round's join code */
    public String roundCode() { return roundCode; }

    /** the sips */
    public int sips() { return sips; }

    /** where the sips came from */
    public SipSource source() { return source; }

    /** whether the player plays with water */
    public boolean water() { return water; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    /** @return the handler list Bukkit requires for every event */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
