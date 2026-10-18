package dev.louismudrack.prost.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** The roulette hit a player and landed on sips (they then arrive as a SipsEvent with source ROULETTE). */
public final class RouletteHitEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID player;
    private final String roundCode;
    private final int sips;

    /**
     * @param player    the player the roulette hit
     * @param roundCode the round's join code
     * @param sips      the sips it landed on
     */
    public RouletteHitEvent(UUID player, String roundCode, int sips) {
        this.player = player;
        this.roundCode = roundCode;
        this.sips = sips;
    }

    /** the player the roulette hit */
    public UUID player() { return player; }

    /** the round's join code */
    public String roundCode() { return roundCode; }

    /** the sips it landed on */
    public int sips() { return sips; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    /** @return the handler list Bukkit requires for every event */
    public static HandlerList getHandlerList() { return HANDLERS; }
}
