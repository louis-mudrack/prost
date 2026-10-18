package dev.louismudrack.prost.api;

import org.bukkit.entity.Player;

/** Tells Prost whether a player is away from keyboard. */
@FunctionalInterface
public interface AfkProvider {
    /** true when the player is AFK */
    boolean isAfk(Player player);
}
