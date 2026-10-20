package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.Items;
import dev.louismudrack.prost.tracker.LuckRules.Prize;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Three chests, solo: one hides a drink, one a joker, one a hand-out; the player opens one. */
final class LuckChests extends LuckTracker {
    private static final int[] CHEST_SLOTS = {LEFT, MIDDLE, RIGHT};
    private static final Prize[] PRIZES = Prize.values();

    /** what each chest hides, filled when one is opened */
    private Prize[] chests;

    LuckChests(TrackContext ctx) { super(ctx); }

    /** a chest's prize is drawn when it is opened */
    @Override
    protected boolean accept(UUID id, int option) {
        choices.put(id, option);
        fillChests(option);
        return true;
    }

    @Override
    protected int randomOption(UUID id) { return rnd().nextInt(CHEST_SLOTS.length); }

    @Override
    protected Verdict judge(Map<UUID, Integer> picks, int sips) { return LuckRules.chests(first, chests[picks.get(first)], sips); }

    @Override
    protected String line(UUID viewer, Map<UUID, Integer> picks) {
        return Msg.str(viewer, "game.luck.result.chests", V.of("name", name(first)), V.mm("prize", prizeName(viewer, chests[picks.get(first)])));
    }

    @Override
    public String stakes(UUID viewer) { return Msg.str(viewer, "challenge.stakes.chests", sips(viewer), give(viewer)); }

    /** the opened chest flickers through the prizes, then all three open */
    @Override
    protected void draw(GameMenu m, UUID viewer) {
        for (int i = 0; i < CHEST_SLOTS.length; i++) {
            ItemStack icon;
            if (revealing() && settled()) icon = prize(viewer, chests[i], mark(viewer, i));
            else if (revealing() && picked(viewer, i)) icon = prize(viewer, PRIZES[rnd().nextInt(PRIZES.length)], mark(viewer, i));
            else icon = Items.item(picked(viewer, i) ? Material.ENDER_CHEST : Material.CHEST,
                    "<hl>" + Msg.str(viewer, "game.luck.chest", V.of("n", i + 1)), mark(viewer, i));
            m.button(CHEST_SLOTS[i], icon, choose(viewer, i));
        }
    }

    /** the opened chest's prize first (one draw), the other two chests get the rest */
    private void fillChests(int opened) {
        List<Prize> rest = new ArrayList<>(List.of(PRIZES));
        Prize prize = rest.remove(rnd().nextInt(rest.size()));
        Collections.shuffle(rest, rnd());
        chests = new Prize[PRIZES.length];
        for (int i = 0; i < chests.length; i++) chests[i] = i == opened ? prize : rest.remove(0);
    }

    private ItemStack prize(UUID viewer, Prize prize, List<String> lore) {
        Material icon = switch (prize) {
            case DRINK -> Material.POTION;
            case JOKER -> Material.NETHER_STAR;
            case HANDOUT -> Material.HONEY_BOTTLE;
        };
        return Items.item(icon, "<hl>" + prizeName(viewer, prize), lore);
    }

    private String prizeName(UUID viewer, Prize prize) {
        int n = prize == Prize.HANDOUT ? ctx.def().give() : ctx.def().sips();
        return Msg.str(viewer, "game.luck.prize." + prize.name().toLowerCase(Locale.ROOT), V.mm("sips", Msg.sips(viewer, n)));
    }
}
