package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.reward.Reward;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** challenges.yml (as loaded by SnakeYAML) -> ChallengeDefs. A broken entry costs that entry, never the catalogue. */
final class CatalogueParser {
    record Parsed(List<ChallengeDef> defs, List<String> problems) {}

    private CatalogueParser() {}

    static Parsed parse(Map<?, ?> root) {
        List<ChallengeDef> defs = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (root == null) return new Parsed(defs, problems);
        for (Map.Entry<?, ?> e : root.entrySet()) {
            String id = String.valueOf(e.getKey());
            if (!(e.getValue() instanceof Map<?, ?> m)) {
                problems.add(id + ": not a section");
                continue;
            }
            try {
                defs.add(entry(id, m));
            } catch (IllegalArgumentException ex) {
                problems.add(id + ": " + ex.getMessage());
            }
        }
        return new Parsed(defs, problems);
    }

    static ChallengeDef entry(String id, Map<?, ?> m) {
        Map<?, ?> name = section(m, "name"), text = section(m, "text");
        int sips = number(m, "sips", -1);
        List<Kind> kinds = new ArrayList<>();
        for (String k : strings(m, "kinds")) {
            Kind kind = Kind.of(k);
            if (kind == null) throw new IllegalArgumentException("unknown kind " + k);
            kinds.add(kind);
        }
        List<Reward> rewards = new ArrayList<>();
        for (String r : strings(m, "rewards")) {
            Reward reward = Reward.of(r);
            if (reward == null) throw new IllegalArgumentException("unknown reward " + r);
            rewards.add(reward);
        }
        Set<Need> needs = EnumSet.noneOf(Need.class);
        for (String n : strings(m, "needs")) {
            Need need = Need.of(n);
            if (need == null) throw new IllegalArgumentException("unknown need " + n);
            needs.add(need);
        }
        return new ChallengeDef(id, text(name, "de"), text(name, "en"), text(text, "de"), text(text, "en"), text(m, "tracker"),
                text(m, "stat"), text(m, "source"), text(m, "mode"), text(m, "value"), strings(m, "targets"),
                number(m, "amount", 1), number(m, "seconds", -1), sips, number(m, "give", sips), List.copyOf(kinds),
                List.copyOf(rewards), Set.copyOf(needs), strings(m, "also-avoid"), number(m, "min-players", 1), number(m, "weight", 1));
    }

    private static Map<?, ?> section(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Map<?, ?> s) return s;
        throw new IllegalArgumentException("missing section " + key);
    }

    private static String text(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static int number(Map<?, ?> m, String key, int fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " is not a number: " + v);
        }
    }

    private static List<String> strings(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) return List.of();
        if (v instanceof List<?> l) return l.stream().map(String::valueOf).toList();
        return List.of(String.valueOf(v));
    }
}
