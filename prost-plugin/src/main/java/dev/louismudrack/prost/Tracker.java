package dev.louismudrack.prost;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Measures one challenge for its participants from the moment it starts (spec §5). */
interface Tracker {
    void start(List<Player> players);

    /** progress in the entry's unit; for avoid: 1 while not broken */
    int progress(UUID id);

    default boolean broken(UUID id) { return false; }

    /** called by the run every 10 ticks with the participants who are online */
    default void poll(List<Player> online) { }

    /** must unregister every listener */
    void stop();
}
