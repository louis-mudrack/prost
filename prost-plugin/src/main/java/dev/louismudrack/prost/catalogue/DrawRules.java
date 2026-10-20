package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.game.Kind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Which challenge comes next: only what is possible at this moment - Nether/End entries only while that
 * dimension is open (owner 2026-10-15), time and weather needs, "enter X" never while someone is already there, no
 * reaction someone cannot do where they are, kinds that fit the number of active members, no repeats until the pool
 * runs dry; then kind by kind-weights and entry by its weight. Solo goes to the member with the fewest solos, Duel to
 * the two with the fewest duels.
 */
public final class DrawRules {
    /** anyoneFull: an active member's food bar is full, so they cannot eat */
    public record Conditions(boolean netherOpen, boolean endOpen, boolean night, boolean rain, boolean thunder,
                      boolean anyoneInNether, boolean anyoneInEnd, boolean anyoneFull) {}

    /** what a player in that dimension can get into their hand in a reaction's time: the ground it is made of */
    private static final Set<String> NETHER_GROUND = Set.of("NETHERRACK", "SOUL_SAND", "SOUL_SOIL", "GRAVEL", "BASALT", "BLACKSTONE",
            "MAGMA_BLOCK");
    private static final Set<String> END_GROUND = Set.of("END_STONE");

    public record Choice(ChallengeDef def, Kind kind) {}

    public record Draw(Choice choice, boolean poolReset) {}

    private DrawRules() {}

    public static boolean feasible(ChallengeDef d, Conditions c) {
        for (Need n : d.needs()) {
            boolean ok = switch (n) {
                case NETHER -> c.netherOpen();
                case END -> c.endOpen();
                case NIGHT -> c.night();
                case DAY -> !c.night();
                case RAIN -> c.rain();
                case THUNDER -> c.thunder();
            };
            if (!ok) return false;
        }
        if (d.entersDimension("nether") && c.anyoneInNether()) return false;
        if (d.entersDimension("the_end") && c.anyoneInEnd()) return false;
        return !"reaction".equals(d.tracker()) || doable(d, c);
    }

    /** water boils away in the Nether, a full food bar cannot eat, and the overworld's dirt is not to be had elsewhere */
    private static boolean doable(ChallengeDef d, Conditions c) {
        return switch (d.mode()) {
            case "water" -> !c.anyoneInNether();
            case "eat" -> !c.anyoneFull();
            case "hold" -> (!c.anyoneInNether() || holdsAny(d, NETHER_GROUND)) && (!c.anyoneInEnd() || holdsAny(d, END_GROUND));
            default -> true;
        };
    }

    private static boolean holdsAny(ChallengeDef d, Set<String> ground) {
        List<String> targets = Tags.resolveAll(d.targets());
        return targets != null && targets.stream().anyMatch(t -> ground.contains(t));
    }

    public static List<Kind> kinds(ChallengeDef d, int active) {
        List<Kind> out = new ArrayList<>();
        if (active < Math.max(1, d.minPlayers())) return out;
        for (Kind k : d.kinds()) if (active == 1 ? k == Kind.SOLO : active >= k.minPlayers) out.add(k);
        return out;
    }

    static List<Choice> eligible(List<ChallengeDef> all, Set<String> used, int active, Conditions c) {
        List<Choice> out = new ArrayList<>();
        for (ChallengeDef d : all) {
            if (used.contains(d.id()) || !feasible(d, c)) continue;
            for (Kind k : kinds(d, active)) out.add(new Choice(d, k));
        }
        return out;
    }

    public static Draw draw(List<ChallengeDef> all, Set<String> used, int active, Conditions c, Map<Kind, Integer> kindWeights, Random rnd) {
        List<Choice> pool = eligible(all, used, active, c);
        boolean reset = false;
        if (pool.isEmpty()) {
            pool = eligible(all, Set.of(), active, c);
            reset = true;
        }
        Choice pick = pick(pool, kindWeights, rnd);
        return pick == null ? null : new Draw(pick, reset);
    }

    /** n different challenges for "pick the next one" */
    public static List<Choice> cards(List<ChallengeDef> all, Set<String> used, int active, Conditions c, Map<Kind, Integer> kindWeights,
                              Random rnd, int n) {
        List<Choice> out = new ArrayList<>();
        Set<String> taken = new HashSet<>(used);
        for (int i = 0; i < n; i++) {
            Choice pick = pick(eligible(all, taken, active, c), kindWeights, rnd);
            if (pick == null) break;
            out.add(pick);
            taken.add(pick.def().id());
        }
        return out;
    }

    private static Choice pick(List<Choice> pool, Map<Kind, Integer> kindWeights, Random rnd) {
        if (pool.isEmpty()) return null;
        Set<Kind> present = new LinkedHashSet<>();
        for (Choice ch : pool) present.add(ch.kind());
        int total = 0;
        for (Kind k : present) total += Math.max(0, kindWeights.getOrDefault(k, 0));
        Kind kind = null;
        if (total == 0) {
            List<Kind> ks = new ArrayList<>(present);
            kind = ks.get(rnd.nextInt(ks.size()));
        } else {
            int r = rnd.nextInt(total);
            for (Kind k : present) {
                r -= Math.max(0, kindWeights.getOrDefault(k, 0));
                if (r < 0) { kind = k; break; }
            }
        }
        final Kind chosen = kind;
        List<Choice> ofKind = pool.stream().filter(ch -> ch.kind() == chosen).toList();
        int sum = 0;
        for (Choice ch : ofKind) sum += Math.max(1, ch.def().weight());
        int r = rnd.nextInt(sum);
        for (Choice ch : ofKind) {
            r -= Math.max(1, ch.def().weight());
            if (r < 0) return ch;
        }
        return ofKind.get(ofKind.size() - 1);
    }

    public static UUID fewest(Map<UUID, Integer> counts, List<UUID> candidates, Random rnd) {
        List<UUID> two = lowest(counts, candidates, rnd, 1);
        return two.isEmpty() ? null : two.get(0);
    }

    public static List<UUID> fewestTwo(Map<UUID, Integer> counts, List<UUID> candidates, Random rnd) { return lowest(counts, candidates, rnd, 2); }

    private static List<UUID> lowest(Map<UUID, Integer> counts, List<UUID> candidates, Random rnd, int n) {
        List<UUID> shuffled = new ArrayList<>(candidates);
        java.util.Collections.shuffle(shuffled, rnd);
        shuffled.sort((x, y) -> Integer.compare(counts.getOrDefault(x, 0), counts.getOrDefault(y, 0)));
        return shuffled.subList(0, Math.min(n, shuffled.size()));
    }
}
