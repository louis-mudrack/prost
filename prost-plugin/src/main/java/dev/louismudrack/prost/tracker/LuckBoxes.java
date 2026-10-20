package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.Items;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The mystery boxes: everybody takes a box, one hides the trap and its taker drinks. */
final class LuckBoxes extends LuckTracker {
    private int trap, boxes;
    /** the trap box's taker who dropped out: the result says so instead of "nobody took it" */
    private UUID trapTaker;

    LuckBoxes(TrackContext ctx) { super(ctx); }

    @Override
    protected void setUp() {
        boxes = Math.min(order.size(), MenuSlots.MAX);
        trap = rnd().nextInt(boxes);
    }

    /** a taken box cannot be picked */
    @Override
    protected boolean accept(UUID id, int option) {
        if (option < 0 || takerOf(option) != null) return false;
        choices.put(id, option);
        if (option == trap) trapTaker = null;
        return true;
    }

    @Override
    protected int randomOption(UUID id) {
        List<Integer> free = new ArrayList<>();
        for (int b = 0; b < boxes; b++) if (takerOf(b) == null) free.add(b);
        return free.isEmpty() ? -1 : free.get(rnd().nextInt(free.size()));
    }

    @Override
    protected void released(UUID id, Integer box) {
        if (box != null && box == trap) trapTaker = id;
    }

    @Override
    protected Verdict judge(Map<UUID, Integer> picks, int sips) { return LuckRules.boxes(picks, trap, sips); }

    @Override
    protected String line(UUID viewer, Map<UUID, Integer> picks) {
        UUID taker = takerOf(trap);
        if (taker != null) return Msg.str(viewer, "game.luck.result.boxes", V.of("box", trap + 1), V.of("name", name(taker)));
        return trapTaker != null ? Msg.str(viewer, "game.luck.result.boxes_left", V.of("box", trap + 1), V.of("name", name(trapTaker)))
                : Msg.str(viewer, "game.luck.result.boxes_free", V.of("box", trap + 1));
    }

    @Override
    public String stakes(UUID viewer) { return Msg.str(viewer, "challenge.stakes.boxes", sips(viewer)); }

    /** a taken box shows its taker's head and cannot be picked; the reveal shakes a random box, then shows the trap */
    @Override
    protected void draw(GameMenu m, UUID viewer) {
        int shaken = revealing() && !settled() ? rnd().nextInt(boxes) : -1;
        for (int b = 0; b < boxes; b++) {
            UUID taker = takerOf(b);
            String label = Msg.str(viewer, "game.luck.box", V.of("n", b + 1));
            List<String> takerLore = taker == null ? List.of() : List.of("<white>" + Msg.esc(name(taker)));
            ItemStack icon;
            if (revealing() && b == (settled() ? trap : shaken)) icon = Items.item(Material.TNT, "<err>" + Msg.str(viewer, "game.luck.trap"), takerLore);
            else if (taker != null) icon = Items.head(taker, "<hl>" + label, takerLore);
            else icon = Items.item(Material.BARREL, "<hl>" + label, List.of());
            m.button(MenuSlots.of(b, boxes), icon, taker == null ? choose(viewer, b) : null);
        }
    }

    private UUID takerOf(int box) {
        for (Map.Entry<UUID, Integer> e : choices.entrySet()) if (e.getValue() == box) return e.getKey();
        return null;
    }
}
