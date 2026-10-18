package dev.louismudrack.prost;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** "#tag" targets of challenges.yml as fixed name lists (Bukkit tags need a running server; these are checked at build). */
final class Tags {
    private static final List<String> COLOURS = List.of("WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK",
            "GRAY", "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK");
    private static final String DEEPSLATE = "DEEPSLATE_";

    static final Map<String, List<String>> TAGS = Map.ofEntries(
            Map.entry("logs", List.of("OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG", "ACACIA_LOG", "DARK_OAK_LOG",
                    "MANGROVE_LOG", "CHERRY_LOG", "PALE_OAK_LOG")),
            Map.entry("flowers", List.of("DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "RED_TULIP", "ORANGE_TULIP",
                    "WHITE_TULIP", "PINK_TULIP", "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY", "TORCHFLOWER", "SUNFLOWER",
                    "LILAC", "ROSE_BUSH", "PEONY")),
            Map.entry("saplings", List.of("OAK_SAPLING", "SPRUCE_SAPLING", "BIRCH_SAPLING", "JUNGLE_SAPLING", "ACACIA_SAPLING",
                    "DARK_OAK_SAPLING", "CHERRY_SAPLING", "PALE_OAK_SAPLING", "MANGROVE_PROPAGULE")),
            Map.entry("wool", COLOURS.stream().map(c -> c + "_WOOL").toList()),
            Map.entry("beds", COLOURS.stream().map(c -> c + "_BED").toList()),
            Map.entry("hostile", List.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "SKELETON", "STRAY", "BOGGED", "CREEPER",
                    "SPIDER", "CAVE_SPIDER", "WITCH", "ENDERMAN", "PHANTOM", "SLIME", "PILLAGER", "VINDICATOR", "GUARDIAN")),
            Map.entry("passive", List.of("COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "GOAT")),
            Map.entry("raw_meat", List.of("BEEF", "PORKCHOP", "CHICKEN", "MUTTON", "RABBIT", "COD", "SALMON")),
            Map.entry("food", List.of("BREAD", "APPLE", "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "COOKED_MUTTON",
                    "COOKED_COD", "COOKED_SALMON", "BAKED_POTATO", "CARROT", "MELON_SLICE", "PUMPKIN_PIE", "COOKIE", "SWEET_BERRIES",
                    "GOLDEN_CARROT", "BEETROOT", "DRIED_KELP", "MUSHROOM_STEW", "RABBIT_STEW", "BEETROOT_SOUP")),
            Map.entry("ores_overworld", List.of("COAL_ORE", "DEEPSLATE_COAL_ORE", "COPPER_ORE", "DEEPSLATE_COPPER_ORE", "IRON_ORE",
                    "DEEPSLATE_IRON_ORE", "GOLD_ORE", "DEEPSLATE_GOLD_ORE", "REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE", "LAPIS_ORE",
                    "DEEPSLATE_LAPIS_ORE", "DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE", "EMERALD_ORE", "DEEPSLATE_EMERALD_ORE")),
            Map.entry("zombies", List.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER")),
            Map.entry("skeletons", List.of("SKELETON", "STRAY", "BOGGED")));

    private Tags() {}

    /** the names behind a target: a "#tag" list, or the target itself; null for an unknown tag */
    static List<String> resolve(String target) {
        if (!target.startsWith("#")) return List.of(target);
        return TAGS.get(target.substring(1));
    }

    /** all targets flattened; null if any tag is unknown */
    static List<String> resolveAll(List<String> targets) {
        List<String> out = new ArrayList<>();
        for (String t : targets) {
            List<String> names = resolve(t);
            if (names == null) return null;
            out.addAll(names);
        }
        return out;
    }

    /** "different ores": a deepslate ore is the same ore kind as the stone one */
    static String distinctKey(String name) {
        return name.startsWith(DEEPSLATE) && name.endsWith("_ORE") ? name.substring(DEEPSLATE.length()) : name;
    }
}
