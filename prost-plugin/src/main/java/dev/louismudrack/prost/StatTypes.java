package dev.louismudrack.prost;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The vanilla statistics challenges may use, what their targets are, and the unit shown to players. */
final class StatTypes {
    enum Target { NONE, MATERIAL, ENTITY }

    private static final int CM_PER_BLOCK = 100, TICKS_PER_SECOND = 20;

    static final Map<String, Target> KNOWN = Map.ofEntries(
            Map.entry("KILL_ENTITY", Target.ENTITY), Map.entry("CRAFT_ITEM", Target.MATERIAL), Map.entry("USE_ITEM", Target.MATERIAL),
            Map.entry("MINE_BLOCK", Target.MATERIAL), Map.entry("ANIMALS_BRED", Target.NONE), Map.entry("FISH_CAUGHT", Target.NONE),
            Map.entry("WALK_ONE_CM", Target.NONE), Map.entry("SPRINT_ONE_CM", Target.NONE), Map.entry("SWIM_ONE_CM", Target.NONE),
            Map.entry("CLIMB_ONE_CM", Target.NONE), Map.entry("CROUCH_ONE_CM", Target.NONE), Map.entry("BOAT_ONE_CM", Target.NONE),
            Map.entry("MINECART_ONE_CM", Target.NONE), Map.entry("HORSE_ONE_CM", Target.NONE), Map.entry("FALL_ONE_CM", Target.NONE),
            Map.entry("JUMP", Target.NONE), Map.entry("SNEAK_TIME", Target.NONE), Map.entry("SLEEP_IN_BED", Target.NONE),
            Map.entry("TRADED_WITH_VILLAGER", Target.NONE), Map.entry("TALKED_TO_VILLAGER", Target.NONE),
            Map.entry("NOTEBLOCK_PLAYED", Target.NONE), Map.entry("BELL_RING", Target.NONE), Map.entry("FLOWER_POTTED", Target.NONE),
            Map.entry("TARGET_HIT", Target.NONE), Map.entry("CAKE_SLICES_EATEN", Target.NONE), Map.entry("ITEM_ENCHANTED", Target.NONE));

    /** MATERIAL statistics whose targets are blocks; the other MATERIAL ones take items */
    static final Set<String> BLOCK_STATS = Set.of("MINE_BLOCK");

    private StatTypes() {}

    /** "WALK_ONE_CM, SPRINT_ONE_CM" -> both; their values are summed */
    static List<String> split(String stat) {
        return Arrays.stream(stat.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** raw value per shown unit: centimetres -> blocks, ticks -> seconds */
    static int scale(String stat) {
        if (stat.endsWith("_ONE_CM")) return CM_PER_BLOCK;
        if ("SNEAK_TIME".equals(stat)) return TICKS_PER_SECOND;
        return 1;
    }
}
