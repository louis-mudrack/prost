package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** your settings in a round: sips per hour (-5 -1 +1 +5) and water mode; changes apply at once */
public final class JoinMenu extends Menu {
    private final Round round;

    public JoinMenu(ProstPlugin plugin, Player viewer, Round round) {
        super(plugin, viewer);
        this.round = round;
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return text("menu.join.title"); }
    @Override protected Menu back() { return new RoundMenu(plugin, viewer, round); }

    @Override
    protected void draw() {
        Member m = round.member(viewer.getUniqueId());
        if (m == null) return;
        int[] steps = {-5, -1, 1, 5};
        int[] slots = {10, 11, 15, 16};
        for (int i = 0; i < steps.length; i++) {
            int step = steps[i];
            set(slots[i], Items.item(step < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    (step < 0 ? "<err>" : "<ok>") + (step > 0 ? "+" : "") + step, List.of()), e -> {
                m.limit = plugin.cfg().clampLimit(m.limit + step);
                render();
            });
        }
        set(13, Items.item(Material.HONEY_BOTTLE, text("menu.join.limit", V.mm("limit", Msg.sips(viewer.getUniqueId(), m.limit))), lines("menu.join.limit_lore")), null);
        List<String> waterLore = new ArrayList<>();
        waterLore.add(m.water ? text("menu.join.water_on") : text("menu.join.water_off"));
        waterLore.addAll(lines("menu.join.water_lore"));
        set(22, Items.item(m.water ? Material.LIME_DYE : Material.GRAY_DYE, "<hl>" + text("menu.join.water"), waterLore), e -> {
            m.water = !m.water;
            render();
        });
    }

    @Override
    protected void closed() { plugin.rounds().save(); }
}
