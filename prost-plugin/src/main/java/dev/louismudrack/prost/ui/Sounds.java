package dev.louismudrack.prost.ui;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** UX standard §4 sounds plus the game's own (a won challenge uses the quieter level-up sound, like AniCore achievements). */
public final class Sounds {
    private Sounds() {}

    public static void open(Player p) { p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f); }
    public static void click(Player p) { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f); }
    public static void success(Player p) { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f); }
    public static void error(Player p) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f); }
    public static void bell(Player p) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f); }
    public static void win(Player p) { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f); }
    public static void fail(Player p) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f); }
    public static void tick(Player p, float pitch) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, pitch); }
    public static void ding(Player p) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f); }
}
