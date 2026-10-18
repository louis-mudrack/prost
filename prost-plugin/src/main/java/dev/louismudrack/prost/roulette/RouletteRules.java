package dev.louismudrack.prost.roulette;

import java.util.List;
import java.util.Map;

/**
 * The random events (spec §8): a chance per action category, at most maxHits per rolling window and a minimum gap
 * between two hits per member, then a weighted spin of 1..weights.length sips.
 */
public final class RouletteRules {
    public record Settings(Map<String, Double> chances, int maxHits, long windowMs, long minGapMs, int[] weights) {}

    private RouletteRules() {}

    static boolean capped(List<Long> hits, long now, Settings s) {
        int inWindow = 0;
        long last = Long.MIN_VALUE;
        for (long h : hits) {
            if (now - h < s.windowMs()) inWindow++;
            last = Math.max(last, h);
        }
        return inWindow >= s.maxHits() || (last != Long.MIN_VALUE && now - last < s.minGapMs());
    }

    static boolean triggers(Settings s, String category, double random) {
        Double chance = s.chances().get(category);
        return chance != null && random < chance;
    }

    static int spin(int[] weights, double random) {
        int total = 0;
        for (int w : weights) total += w;
        double r = random * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) return i + 1;
        }
        return weights.length;
    }

    static void prune(List<Long> hits, long now, long windowMs) { hits.removeIf(h -> now - h >= windowMs); }
}
