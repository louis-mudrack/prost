package dev.louismudrack.prost.tracker;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * A tracker that runs a game with its own rules instead of measuring progress: it decides when the run ends and who
 * drinks or wins. The run polls it like any tracker and reads time only through ProstPlugin#now.
 */
public interface GameTracker extends Tracker {
    /** epoch ms; may move while the game runs */
    long deadline();

    /** true ends the run before the deadline */
    boolean decided();

    /** called once, after the last poll */
    Verdict verdict();

    /** MiniMessage: how {@code who} stands, worded for {@code viewer}; the boss bar and the action bar show it */
    String status(UUID viewer, UUID who);

    /** MiniMessage that replaces the default result line for {@code viewer}; null keeps the default */
    default String result(UUID viewer) { return null; }

    /** MiniMessage: what is at stake, worded for {@code viewer}; null keeps the kind's own challenge.stakes line */
    default String stakes(UUID viewer) { return null; }

    /** the clock reads "??" */
    default boolean hiddenClock() { return false; }

    /** the participant left the run (not active any more) */
    default void dropped(UUID id) { }

    /** /prost play: opens the game's menu again; false when there is none to open */
    default boolean reopen(Player p) { return false; }

    /** non-null: the run cancels with this language key and nobody drinks */
    default String cancelKey() { return null; }
}
