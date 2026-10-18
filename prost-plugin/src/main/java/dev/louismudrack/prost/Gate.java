package dev.louismudrack.prost;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The integrations until the hook API exists (Task 6): English for everyone (AniDrinks fell back to German),
 * nobody AFK, no bots, Nether and End closed, nowhere excluded.
 */
final class Gate {
    private Gate() {}

    static String lang(UUID id) { return "en"; }

    static boolean afk(UUID id) { return false; }

    static boolean bot(Player p) { return false; }

    static boolean netherOpen() { return false; }

    static boolean endOpen() { return false; }

    static boolean inSpawn(Location l) { return false; }
}
