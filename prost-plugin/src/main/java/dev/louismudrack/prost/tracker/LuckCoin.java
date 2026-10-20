package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.Items;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The coin, a duel: only the caller calls heads or tails, a right call makes the other one drink. */
final class LuckCoin extends LuckTracker {
    private static final int HEADS = 1, TAILS = 0, TWO_SIDES = 2;

    private boolean heads;

    LuckCoin(TrackContext ctx) { super(ctx); }

    @Override
    protected void setUp() { heads = rnd().nextBoolean(); }

    @Override
    protected boolean decides(UUID id) { return id.equals(first); }

    @Override
    protected String watching(UUID viewer, boolean own) {
        return own ? Msg.str(viewer, "game.luck.calls", V.of("name", name(first))) : super.watching(viewer, own);
    }

    @Override
    protected int randomOption(UUID id) { return rnd().nextInt(TWO_SIDES); }

    @Override
    protected Verdict judge(Map<UUID, Integer> picks, int sips) {
        return LuckRules.coin(first, other(), picks.get(first) == HEADS, heads, sips);
    }

    @Override
    protected String line(UUID viewer, Map<UUID, Integer> picks) {
        return picks.containsKey(first) ? Msg.str(viewer, "game.luck.result.coin", V.of("name", name(first)),
                V.mm("call", side(viewer, picks.get(first) == HEADS)), V.mm("side", side(viewer, heads))) : null;
    }

    @Override
    public String stakes(UUID viewer) { return Msg.str(viewer, "challenge.stakes.coin", sips(viewer)); }

    @Override
    protected void draw(GameMenu m, UUID viewer) {
        m.button(LEFT, coin(viewer, true, mark(viewer, HEADS)), choose(viewer, HEADS));
        m.button(RIGHT, coin(viewer, false, mark(viewer, TAILS)), choose(viewer, TAILS));
        if (revealing()) m.button(MIDDLE, coin(viewer, settled() ? heads : rnd().nextBoolean(), List.of()), null);
    }

    private UUID other() { return order.stream().filter(id -> !id.equals(first)).findFirst().orElseThrow(); }

    private static ItemStack coin(UUID viewer, boolean heads, List<String> lore) {
        return Items.item(heads ? Material.GOLD_NUGGET : Material.IRON_NUGGET, "<hl>" + side(viewer, heads), lore);
    }

    private static String side(UUID viewer, boolean heads) { return Msg.str(viewer, heads ? "game.luck.heads" : "game.luck.tails"); }
}
