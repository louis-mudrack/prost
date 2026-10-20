package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;

/** The die, solo: 1-2 drinks, 3-4 nothing, 5-6 hands out. */
final class LuckDice extends LuckTracker {
    LuckDice(TrackContext ctx) { super(ctx); }

    /** the roll is made here, not by the click */
    @Override
    protected boolean accept(UUID id, int option) {
        choices.put(id, rnd().nextInt(LuckRules.DIE_MAX) + 1);
        return true;
    }

    @Override
    protected int randomOption(UUID id) { return 0; }

    @Override
    protected Verdict judge(Map<UUID, Integer> picks, int sips) { return LuckRules.dice(first, picks.get(first), sips); }

    @Override
    protected String line(UUID viewer, Map<UUID, Integer> picks) {
        return Msg.str(viewer, "game.luck.result.dice", V.of("name", name(first)), V.of("roll", picks.get(first)));
    }

    @Override
    public String stakes(UUID viewer) { return Msg.str(viewer, "challenge.stakes.dice", sips(viewer), give(viewer)); }

    @Override
    protected void draw(GameMenu m, UUID viewer) { drawRoll(m, viewer, Material.BONE, "game.luck.roll", LuckRules.DIE_MAX); }
}
