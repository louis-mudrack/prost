package dev.louismudrack.prost.hook;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The integrations until the hook API exists (Task 6): English for everyone (AniDrinks fell back to German),
 * nobody AFK, no bots, Nether and End closed, nowhere excluded.
 */
public final class Gate {
    private Gate() {}

    public static String lang(UUID id) { return "en"; }

    public static boolean afk(UUID id) { return false; }

    public static boolean bot(Player p) { return false; }

    public static boolean netherOpen() { return false; }

    public static boolean endOpen() { return false; }

    public static boolean inSpawn(Location l) { return false; }
}
