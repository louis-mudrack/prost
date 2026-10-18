package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** your settings in a round: sips per hour (-5 -1 +1 +5) and water mode (spec §3.2, §9); changes apply at once */
public final class JoinMenu extends Menu {
    private final Round round;

    public JoinMenu(ProstPlugin plugin, Player viewer, Round round) {
        super(plugin, viewer);
        this.round = round;
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return t("Deine Einstellungen", "Your settings"); }
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
                    (step < 0 ? Msg.ERR : Msg.OK) + (step > 0 ? "+" : "") + step, List.of()), e -> {
                m.limit = plugin.cfg().clampLimit(m.limit + step);
                render();
            });
        }
        set(13, Items.item(Material.HONEY_BOTTLE, Msg.HL + t("Limit: ", "Limit: ") + m.limit + t(" Schlucke pro Stunde", " sips per hour"),
                List.of("<gray>" + t("Was darüber geht, verfällt.", "Anything above it lapses."))), null);
        set(22, Items.item(m.water ? Material.LIME_DYE : Material.GRAY_DYE, Msg.HL + t("Wasser-Modus", "Water mode"),
                List.of(m.water ? Msg.OK + t("an", "on") : Msg.ERR + t("aus", "off"), "<gray>" + t("Du trinkst etwas ohne Alkohol.", "You drink something without alcohol."))), e -> {
            m.water = !m.water;
            render();
        });
    }

    @Override
    protected void closed() { plugin.rounds().save(); }
}
