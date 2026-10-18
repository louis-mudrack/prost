package dev.louismudrack.prost.api;

import org.bukkit.Location;

/** Marks positions that never count for reach goals. */
@FunctionalInterface
public interface AreaFilter {
    /** true when the location is excluded */
    boolean isExcluded(Location location);
}
