package dev.louismudrack.prost.roulette;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static dev.louismudrack.prost.Checks.*;

class RouletteRulesTest {
    @Test
    void roulette() {
        long min = 60_000L;
        RouletteRules.Settings s = new RouletteRules.Settings(Map.of("block", 0.003, "kill", 0.02), 3, 30 * min, 4 * min,
                new int[]{40, 30, 20, 10});
        check("no hits yet: not capped", !RouletteRules.capped(List.of(), 100 * min, s));
        check("inside the 4-minute gap: capped", RouletteRules.capped(List.of(97 * min), 100 * min, s));
        check("after the gap: free", !RouletteRules.capped(List.of(95 * min), 100 * min, s));
        check("three hits in 30 minutes: capped", RouletteRules.capped(List.of(75 * min, 82 * min, 90 * min), 100 * min, s));
        check("the oldest left the window: free again", !RouletteRules.capped(List.of(70 * min, 82 * min, 90 * min), 100 * min, s));
        List<Long> hits = new ArrayList<>(List.of(60 * min, 82 * min));
        RouletteRules.prune(hits, 100 * min, 30 * min);
        eq("prune keeps only the window", List.of(82 * min), hits);

        check("a roll under the chance triggers", RouletteRules.triggers(s, "kill", 0.019));
        check("a roll at the chance does not", !RouletteRules.triggers(s, "kill", 0.02));
        check("an unknown category never triggers", !RouletteRules.triggers(s, "jump", 0.0));

        eq("lowest roll = 1 sip", 1, RouletteRules.spin(s.weights(), 0.0));
        eq("just under 40 % = still 1", 1, RouletteRules.spin(s.weights(), 0.399));
        eq("40 % = 2", 2, RouletteRules.spin(s.weights(), 0.40));
        eq("highest roll = 4", 4, RouletteRules.spin(s.weights(), 0.9999));
        int[] counts = new int[5];
        Random r = new Random(42);
        for (int i = 0; i < 100_000; i++) counts[RouletteRules.spin(s.weights(), r.nextDouble())]++;
        check("over 100 000 spins the shares match 40/30/20/10 within 1 %",
                Math.abs(counts[1] - 40_000) < 1_000 && Math.abs(counts[2] - 30_000) < 1_000
                        && Math.abs(counts[3] - 20_000) < 1_000 && Math.abs(counts[4] - 10_000) < 1_000);
    }
}
