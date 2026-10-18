package dev.louismudrack.prost.config;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.game.Pace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.louismudrack.prost.Checks.*;

class DrinksConfigTest {
    @Test
    void config() {
        DrinksConfig d = DrinksConfig.DEFAULTS;
        eq("max players", 8, d.maxPlayers());
        eq("warm-up", 120, d.warmupSeconds());
        eq("party pace window", "[240, 420]", java.util.Arrays.toString(d.pace().get(Pace.PARTY)));
        eq("kind weight each", 25, d.kindWeights().get(Kind.EACH));
        eq("joker max", 2, d.jokerMax());
        eq("limit default", 20, d.limitDefault());
        eq("roulette window 30 min", 30 * 60_000L, d.roulette().windowMs());
        eq("roulette gap 4 min", 4 * 60_000L, d.roulette().minGapMs());
        eq("roulette weights", "[40, 30, 20, 10]", java.util.Arrays.toString(d.roulette().weights()));
        eq("block chance", 0.003, d.roulette().chances().get("block"));
        eq("rules max", 5, d.rulesMaxActive());

        DrinksConfig c = DrinksConfig.parse(Map.of("max-players", 4, "pace", Map.of("party", List.of(60, 120)),
                "limit", Map.of("default", 10, "min", 5, "max", 40), "roulette", Map.of("max-hits", 5, "chances", Map.of("block", 0.01))));
        eq("override max players", 4, c.maxPlayers());
        eq("override one pace, keep the others", "[60, 120]", java.util.Arrays.toString(c.pace().get(Pace.PARTY)));
        eq("other paces keep defaults", "[480, 720]", java.util.Arrays.toString(c.pace().get(Pace.NORMAL)));
        eq("override a chance", 0.01, c.roulette().chances().get("block"));
        eq("unlisted chances keep defaults", 0.02, c.roulette().chances().get("kill"));
        eq("override max hits", 5, c.roulette().maxHits());

        DrinksConfig bad = DrinksConfig.parse(Map.of("max-players", "lots", "pace", Map.of("chill", List.of(900, 100)),
                "limit", Map.of("min", 50, "max", 10)));
        eq("a word instead of a number keeps the default", 8, bad.maxPlayers());
        eq("a reversed window keeps the default", "[720, 1080]", java.util.Arrays.toString(bad.pace().get(Pace.CHILL)));
        eq("min above max keeps the defaults", 5, bad.limitMin());
        eq("clamp below", 5, DrinksConfig.DEFAULTS.clampLimit(1));
        eq("clamp above", 40, DrinksConfig.DEFAULTS.clampLimit(99));

    }
}
