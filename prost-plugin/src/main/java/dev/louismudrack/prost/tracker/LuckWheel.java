package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;

/** The wheel: everybody spins 1-10, the lowest drinks; alone (or solo) 1-3 drinks and the rest wins. */
final class LuckWheel extends LuckTracker {
    LuckWheel(TrackContext ctx) { super(ctx); }

    /** the spin is rolled here, not by the click */
    @Override
    protected boolean accept(UUID id, int option) {
        choices.put(id, rnd().nextInt(LuckRules.WHEEL_MAX) + 1);
        return true;
    }

    @Override
    protected int randomOption(UUID id) { return 0; }

    @Override
    protected Verdict judge(Map<UUID, Integer> picks, int sips) { return LuckRules.wheel(ctx.kind(), picks, sips); }

    @Override
    protected String line(UUID viewer, Map<UUID, Integer> picks) {
        if (aLoneSpinner(picks)) {
            UUID spinner = picks.keySet().iterator().next();
            return Msg.str(viewer, "game.luck.result.wheel_alone", V.of("name", name(spinner)), V.of("roll", picks.get(spinner)));
        }
        return Msg.str(viewer, "game.luck.result.wheel", V.mm("rolls", each(picks, n -> String.valueOf(n))));
    }

    @Override
    public String stakes(UUID viewer) {
        return solo() ? Msg.str(viewer, "challenge.stakes.wheel_solo", sips(viewer), reward(viewer)) : Msg.str(viewer, "challenge.stakes.wheel", sips(viewer));
    }

    @Override
    protected void draw(GameMenu m, UUID viewer) { drawRoll(m, viewer, Material.SUNFLOWER, "game.luck.spin", LuckRules.WHEEL_MAX); }

    /** the rule is the solo one then (LuckRules#wheel), so the line says why */
    private boolean aLoneSpinner(Map<UUID, Integer> picks) { return !solo() && picks.size() == 1; }
}
