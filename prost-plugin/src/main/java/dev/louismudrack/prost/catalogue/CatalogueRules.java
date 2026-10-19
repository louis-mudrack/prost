package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.game.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Semantic checks of one catalogue entry: run on the shipped file at build and on every load. */
final class CatalogueRules {
    /** does a name exist in the running Paper API (reflection at build, Bukkit at runtime) */
    interface Names {
        boolean material(String name);
        boolean entity(String name);
        boolean statistic(String name);
        /** usable as an item (CRAFT_ITEM / USE_ITEM statistics) - Player#getStatistic throws otherwise */
        boolean item(String name);
        /** a placeable block (MINE_BLOCK statistic) */
        boolean block(String name);
    }

    static final Set<String> TRACKERS = Set.of("mine", "stat", "collect", "gift", "inventory", "count", "distinct", "reach",
            "avoid", "wear", "tower", "proximity", "reaction", "potato", "luck");
    static final Set<String> AVOID_MODES = Set.of("damage", "explosion-damage", "death", "jump", "sprint", "eat", "chat",
            "break", "kill", "water", "sleep");
    private static final Set<String> DISTINCT_SOURCES = Set.of("mine", "collect", "consume", "craft", "kill", "biome");
    private static final Set<String> REACH_MODES = Set.of("below", "above", "distance", "dimension", "structure");
    private static final Set<String> PROXIMITY_MODES = Set.of("all", "block", "hug");
    private static final Set<Kind> AVOID_KINDS = Set.of(Kind.TEAM, Kind.EACH, Kind.SOLO);
    static final Set<String> REACTION_MODES = Set.of("crouch", "water", "chat", "look_up", "jump", "hold", "sit", "eat", "freeze");
    private static final Set<Kind> REACTION_KINDS = Set.of(Kind.REACTION, Kind.SOLO);
    /** the game trackers that can be played as kind reaction */
    private static final Set<String> REACTION_TRACKERS = Set.of("reaction", "potato");
    /** each luck game and the kinds it can be played as */
    static final Map<String, Set<Kind>> LUCK_MODES = Map.of("wheel", Set.of(Kind.LUCK, Kind.SOLO), "coin", Set.of(Kind.DUEL),
            "chests", Set.of(Kind.SOLO), "dice", Set.of(Kind.SOLO), "high_low", Set.of(Kind.DUEL, Kind.SOLO), "boxes", Set.of(Kind.LUCK));
    /** trackers that decide the run themselves (GameTracker): also-avoid would wrap them and hide the game from the run */
    private static final Set<String> GAME_TRACKERS = Set.of("reaction", "potato", "luck");
    private static final int MIN_SIPS = 1, MAX_SIPS = 6, MIN_SECONDS = 10, MAX_SECONDS = 900;

    private CatalogueRules() {}

    static List<String> validate(ChallengeDef d, Names names) {
        List<String> p = new ArrayList<>();
        if (d.names().isEmpty() || d.texts().isEmpty()) p.add("needs a name and a text in at least one language");
        if (!TRACKERS.contains(d.tracker())) p.add("unknown tracker '" + d.tracker() + "'");
        if (d.sips() < MIN_SIPS || d.sips() > MAX_SIPS) p.add("sips must be 1-6");
        if (d.seconds() < MIN_SECONDS || d.seconds() > MAX_SECONDS) p.add("seconds must be 10-900");
        if (d.amount() < 1 || d.give() < 1 || d.weight() < 1 || d.minPlayers() < 1) p.add("amount/give/weight/min-players must be >= 1");
        if (d.kinds().isEmpty()) p.add("no kinds");
        if (d.rewards().isEmpty()) p.add("no rewards");
        for (String a : d.alsoAvoid()) if (!AVOID_MODES.contains(a)) p.add("unknown also-avoid '" + a + "'");
        if (!d.alsoAvoid().isEmpty() && GAME_TRACKERS.contains(d.tracker())) p.add("also-avoid does not work with tracker " + d.tracker());
        if (d.kinds().contains(Kind.REACTION) && !REACTION_TRACKERS.contains(d.tracker())) p.add("kind reaction does not work with tracker " + d.tracker());
        if (d.kinds().contains(Kind.LUCK) && !"luck".equals(d.tracker())) p.add("kind luck does not work with tracker " + d.tracker());
        List<String> resolved = Tags.resolveAll(d.targets());
        if (resolved == null) {
            p.add("unknown tag in " + d.targets());
            return p;
        }
        switch (d.tracker()) {
            case "mine", "collect", "gift", "inventory" -> materials(p, resolved, names, true);
            case "wear" -> {
                if (!"head".equals(d.mode())) p.add("wear supports mode head");
                materials(p, resolved, names, true);
            }
            case "stat" -> stat(p, d, resolved, names);
            case "count" -> {
                if ("place".equals(d.mode())) { if (!resolved.isEmpty()) p.add("count place takes no targets"); }
                else if ("consume".equals(d.mode())) materials(p, resolved, names, false);
                else p.add("count mode must be place or consume");
            }
            case "distinct" -> {
                if (!DISTINCT_SOURCES.contains(d.source())) p.add("unknown distinct source '" + d.source() + "'");
                else if ("kill".equals(d.source())) entities(p, resolved, names);
                else if ("biome".equals(d.source())) { if (!resolved.isEmpty()) p.add("distinct biome takes no targets"); }
                else materials(p, resolved, names, false);
            }
            case "reach" -> reach(p, d);
            case "avoid" -> {
                for (String mode : d.mode().split(",")) if (!AVOID_MODES.contains(mode.trim())) p.add("unknown avoid mode '" + mode + "'");
                for (Kind k : d.kinds()) if (!AVOID_KINDS.contains(k)) p.add("avoid cannot be played as " + k.key);
            }
            case "proximity" -> {
                if (!PROXIMITY_MODES.contains(d.mode())) p.add("proximity mode must be all, block or hug");
                if (!isNumber(d.value())) p.add("proximity needs a numeric value");
            }
            case "reaction" -> {
                if (!REACTION_MODES.contains(d.mode())) p.add("unknown reaction mode '" + d.mode() + "'");
                for (Kind k : d.kinds()) if (!REACTION_KINDS.contains(k)) p.add("reaction cannot be played as " + k.key);
                if ("hold".equals(d.mode())) materials(p, resolved, names, true);
            }
            case "potato" -> { for (Kind k : d.kinds()) if (k != Kind.REACTION) p.add("potato cannot be played as " + k.key); }
            case "luck" -> {
                Set<Kind> allowed = LUCK_MODES.get(d.mode());
                if (allowed == null) p.add("unknown luck mode '" + d.mode() + "'");
                else for (Kind k : d.kinds()) if (!allowed.contains(k)) p.add("luck mode " + d.mode() + " cannot be played as " + k.key);
            }
            default -> { }
        }
        for (String name : resolved) {
            Need need = DimensionLocks.required(name);
            if (need != null && !d.needs().contains(need)) p.add(name + " exists only with needs: [" + need.key + "]");
        }
        if (d.entersDimension("nether") && !d.needs().contains(Need.NETHER)) p.add("entering the Nether needs needs: [nether]");
        if (d.entersDimension("the_end") && !d.needs().contains(Need.END)) p.add("entering the End needs needs: [end]");
        return p;
    }

    private static void stat(List<String> p, ChallengeDef d, List<String> resolved, Names names) {
        List<String> stats = StatTypes.split(d.stat());
        if (stats.isEmpty()) p.add("stat missing");
        for (String s : stats) {
            StatTypes.Target t = StatTypes.KNOWN.get(s);
            if (t == null || !names.statistic(s)) {
                p.add("unknown stat '" + s + "'");
                continue;
            }
            switch (t) {
                case NONE -> { if (!resolved.isEmpty()) p.add(s + " takes no targets"); }
                case MATERIAL -> {
                    materials(p, resolved, names, true);
                    boolean blockStat = StatTypes.BLOCK_STATS.contains(s);
                    for (String n : resolved)
                        if (names.material(n) && !(blockStat ? names.block(n) : names.item(n)))
                            p.add(s + " needs " + (blockStat ? "a block" : "an item") + ", " + n + " is none");
                }
                case ENTITY -> entities(p, resolved, names);
            }
        }
    }

    private static void reach(List<String> p, ChallengeDef d) {
        if (!REACH_MODES.contains(d.mode())) {
            p.add("unknown reach mode '" + d.mode() + "'");
            return;
        }
        switch (d.mode()) {
            case "below", "above", "distance" -> { if (!isNumber(d.value())) p.add("reach " + d.mode() + " needs a number"); }
            case "dimension" -> { if (!Set.of("nether", "the_end").contains(d.value())) p.add("dimension must be nether or the_end"); }
            case "structure" -> { if (!"village".equals(d.value())) p.add("structure supports village"); }
            default -> { }
        }
    }

    private static void materials(List<String> p, List<String> resolved, Names names, boolean required) {
        if (required && resolved.isEmpty()) p.add("targets missing");
        for (String n : resolved) if (!names.material(n)) p.add("unknown material " + n);
    }

    private static void entities(List<String> p, List<String> resolved, Names names) {
        if (resolved.isEmpty()) p.add("targets missing");
        for (String n : resolved) if (!names.entity(n)) p.add("unknown entity " + n);
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
