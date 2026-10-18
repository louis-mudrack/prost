package dev.louismudrack.prost.api;

import org.bukkit.entity.Player;

/** Tells Prost which players are bots. */
@FunctionalInterface
public interface BotFilter {
    /** true when the player is a bot and must not play */
    boolean isBot(Player player);
}
