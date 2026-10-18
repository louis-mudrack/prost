package dev.louismudrack.prost;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** drinks.yml (spec §12.1). Missing or invalid values fall back to the spec's defaults, so a half-edited file still works. */
record DrinksConfig(int maxPlayers, int warmupSeconds, Map<Pace, int[]> pace, Map<Kind, Integer> kindWeights, int rewardSeconds,
                    int jokerMax, int jokerOfferSeconds, int limitDefault, int limitMin, int limitMax, RouletteRules.Settings roulette,
                    int rulesMaxActive, int rulesReminderMinutes, int customRuleMaxLength, int offlineGraceMinutes) {

    private static final long MINUTE_MS = 60_000L;

    static final DrinksConfig DEFAULTS = parse(Map.of());

    static DrinksConfig parse(Map<?, ?> y) {
        Map<?, ?> root = y == null ? Map.of() : y;
        Map<Pace, int[]> pace = new EnumMap<>(Pace.class);
        pace.put(Pace.CHILL, window(sub(root, "pace").get("chill"), 720, 1080));
        pace.put(Pace.NORMAL, window(sub(root, "pace").get("normal"), 480, 720));
        pace.put(Pace.PARTY, window(sub(root, "pace").get("party"), 240, 420));
        Map<Kind, Integer> kw = new EnumMap<>(Kind.class);
        Map<?, ?> kws = sub(root, "kind-weights");
        kw.put(Kind.TEAM, num(kws.get("team"), 20));
        kw.put(Kind.EACH, num(kws.get("each"), 25));
        kw.put(Kind.RACE, num(kws.get("race"), 15));
        kw.put(Kind.DUEL, num(kws.get("duel"), 10));
        kw.put(Kind.SOLO, num(kws.get("solo"), 15));
        Map<?, ?> limit = sub(root, "limit");
        int min = num(limit.get("min"), 5), max = num(limit.get("max"), 40);
        if (min < 1 || min > max) { min = 5; max = 40; }
        int def = Math.max(min, Math.min(max, num(limit.get("default"), 20)));
        Map<?, ?> r = sub(root, "roulette");
        Map<String, Double> chances = new HashMap<>(Map.ofEntries(Map.entry("block", 0.003), Map.entry("craft", 0.01),
                Map.entry("kill", 0.02), Map.entry("eat", 0.02), Map.entry("fish", 0.02), Map.entry("breed", 0.02),
                Map.entry("trade", 0.02), Map.entry("enchant", 0.02), Map.entry("advancement", 0.10), Map.entry("death", 0.10),
                Map.entry("sleep", 0.10), Map.entry("boss", 0.25)));
        for (Map.Entry<?, ?> e : sub(r, "chances").entrySet())
            if (e.getValue() instanceof Number n && n.doubleValue() >= 0 && n.doubleValue() <= 1) chances.put(String.valueOf(e.getKey()), n.doubleValue());
        RouletteRules.Settings roulette = new RouletteRules.Settings(Map.copyOf(chances), num(r.get("max-hits"), 3),
                num(r.get("window-minutes"), 30) * MINUTE_MS, num(r.get("min-gap-minutes"), 4) * MINUTE_MS, weights(r.get("weights")));
        Map<?, ?> joker = sub(root, "joker"), rules = sub(root, "rules");
        return new DrinksConfig(num(root.get("max-players"), 8), num(root.get("warmup-seconds"), 120), pace, kw,
                num(root.get("reward-seconds"), 60), num(joker.get("max"), 2), num(joker.get("offer-seconds"), 10), def, min, max,
                roulette, num(rules.get("max-active"), 5), num(rules.get("reminder-minutes"), 10), num(rules.get("custom-max-length"), 60),
                num(root.get("offline-grace-minutes"), 10));
    }

    int clampLimit(int wanted) { return Math.max(limitMin, Math.min(limitMax, wanted)); }

    private static Map<?, ?> sub(Map<?, ?> m, String key) { return m.get(key) instanceof Map<?, ?> s ? s : Map.of(); }

    private static int num(Object v, int fallback) {
        return v instanceof Number n && n.intValue() >= 0 ? n.intValue() : fallback;
    }

    private static int[] window(Object v, int min, int max) {
        if (v instanceof List<?> l && l.size() == 2 && l.get(0) instanceof Number a && l.get(1) instanceof Number b
                && a.intValue() > 0 && a.intValue() <= b.intValue()) return new int[]{a.intValue(), b.intValue()};
        return new int[]{min, max};
    }

    private static int[] weights(Object v) {
        if (v instanceof List<?> l && !l.isEmpty() && l.stream().allMatch(x -> x instanceof Number n && n.intValue() >= 0)
                && l.stream().mapToInt(x -> ((Number) x).intValue()).sum() > 0)
            return l.stream().mapToInt(x -> ((Number) x).intValue()).toArray();
        return new int[]{40, 30, 20, 10};
    }
}
