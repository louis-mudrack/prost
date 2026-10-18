package dev.louismudrack.prost.catalogue;

import java.util.Set;

/**
 * Blocks, items and mobs that only exist in the Nether or the End (spec §4.4). A challenge targeting one must declare
 * needs: [nether] / [end], so it is only drawn while that dimension is open (owner 2026-10-15). Things with an
 * overworld source (ender pearls, glowstone dust from witches, gold nuggets) are deliberately not listed.
 */
final class DimensionLocks {
    static final Set<String> NETHER = Set.of("NETHER_QUARTZ_ORE", "QUARTZ", "ANCIENT_DEBRIS", "NETHERITE_SCRAP", "NETHERRACK",
            "NETHER_GOLD_ORE", "SOUL_SAND", "SOUL_SOIL", "BASALT", "BLACKSTONE", "CRIMSON_STEM", "WARPED_STEM", "CRIMSON_FUNGUS",
            "WARPED_FUNGUS", "CRIMSON_NYLIUM", "WARPED_NYLIUM", "GLOWSTONE", "NETHER_WART", "MAGMA_CREAM", "BLAZE", "BLAZE_ROD",
            "GHAST", "GHAST_TEAR", "MAGMA_CUBE", "PIGLIN", "HOGLIN", "STRIDER", "WITHER_SKELETON", "WITHER_SKELETON_SKULL");
    static final Set<String> END = Set.of("END_STONE", "CHORUS_PLANT", "CHORUS_FRUIT", "PURPUR_BLOCK", "SHULKER", "SHULKER_SHELL",
            "ELYTRA", "DRAGON_BREATH", "ENDER_DRAGON");

    private DimensionLocks() {}

    static Need required(String name) {
        if (NETHER.contains(name)) return Need.NETHER;
        if (END.contains(name)) return Need.END;
        return null;
    }
}
