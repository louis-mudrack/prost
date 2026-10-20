package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.Items;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Higher or lower: guess whether the next number tops the shown one; a wrong guess drinks. */
final class LuckHighLow extends LuckTracker {
    private static final int HIGHER = 1, LOWER = 0, TWO_SIDES = 2;

    private int shown, next;

    LuckHighLow(TrackContext ctx) { super(ctx); }

    @Override
    protected void setUp() {
        shown = rnd().nextInt(LuckRules.NUMBER_MAX) + 1;
        next = LuckRules.nextNumber(shown, rnd());
    }

    @Override
    protected int randomOption(UUID id) { return rnd().nextInt(TWO_SIDES); }

    @Override
    protected Verdict judge(Map<UUID, Integer> picks, int sips) { return LuckRules.highLow(guesses(picks), shown, next, sips); }

    @Override
    protected String line(UUID viewer, Map<UUID, Integer> picks) {
        return Msg.str(viewer, "game.luck.result.high_low", V.of("shown", shown), V.of("next", next),
                V.mm("guesses", each(picks, g -> Msg.str(viewer, g == HIGHER ? "game.luck.higher" : "game.luck.lower"))));
    }

    @Override
    public String stakes(UUID viewer) {
        return solo() ? Msg.str(viewer, "challenge.stakes.high_low_solo", sips(viewer), reward(viewer))
                : Msg.str(viewer, "challenge.stakes.high_low", sips(viewer), reward(viewer));
    }

    @Override
    protected void draw(GameMenu m, UUID viewer) {
        m.button(MIDDLE, number(shown, Msg.str(viewer, "game.luck.shown", V.of("n", shown))), null);
        m.button(LEFT, Items.item(Material.RED_DYE, "<hl>" + Msg.str(viewer, "game.luck.lower"), mark(viewer, LOWER)), choose(viewer, LOWER));
        m.button(RIGHT, Items.item(Material.LIME_DYE, "<hl>" + Msg.str(viewer, "game.luck.higher"), mark(viewer, HIGHER)), choose(viewer, HIGHER));
        if (!revealing()) return;
        int face = settled() ? next : LuckRules.nextNumber(shown, rnd());
        m.button(BELOW, number(face, Msg.str(viewer, "game.luck.next", V.of("n", face))), null);
    }

    private static Map<UUID, Boolean> guesses(Map<UUID, Integer> picks) {
        Map<UUID, Boolean> out = new LinkedHashMap<>();
        picks.forEach((id, guess) -> out.put(id, guess == HIGHER));
        return out;
    }
}
