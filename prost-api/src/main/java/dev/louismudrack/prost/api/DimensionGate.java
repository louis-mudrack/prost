package dev.louismudrack.prost.api;

import org.bukkit.World;

/** Asked for NETHER and THE_END; the overworld is always open. */
@FunctionalInterface
public interface DimensionGate {
    /** true when challenges for this dimension may be drawn */
    boolean isOpen(World.Environment environment);
}
