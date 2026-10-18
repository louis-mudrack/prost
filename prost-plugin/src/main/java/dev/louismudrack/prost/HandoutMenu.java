package dev.louismudrack.prost;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** spread a winner's sips over the eligible members: left-click +1, right-click -1, confirm (spec §4.5) */
final class HandoutMenu extends Menu {
    private final Rewards rewards;
    private final Rewards.Handout handout;

    HandoutMenu(ProstPlugin plugin, Player viewer, Rewards rewards, Rewards.Handout handout) {
        super(plugin, viewer);
        this.rewards = rewards;
        this.handout = handout;
    }

    boolean shows(Rewards.Handout h) { return handout == h; }

    @Override protected int size() { return 27; }
    @Override protected String title() { return t("Schlucke verteilen", "Hand out sips"); }

    @Override
    protected void draw() {
        int slot = 9;
        for (Map.Entry<UUID, Integer> e : handout.room.entrySet()) {
            if (slot > 17) break;
            UUID id = e.getKey();
            Member m = handout.round.member(id);
            if (m == null) continue;
            int given = handout.assigned.getOrDefault(id, 0);
            set(slot++, Items.head(id, Msg.HL + Msg.esc(m.name), List.of(
                    "<gray>" + t("Zugeteilt: ", "Assigned: ") + Msg.HL + given + " <dark_gray>/ " + e.getValue(),
                    "<dark_gray>" + t("Links +1 · Rechts -1", "Left +1 · Right -1"))), click -> {
                if (click.isLeftClick() && handout.remaining() > 0 && given < e.getValue()) handout.assigned.merge(id, 1, Integer::sum);
                else if (click.isRightClick() && given > 0) handout.assigned.merge(id, -1, Integer::sum);
                render();
            });
        }
        set(22, Items.item(Material.LIME_WOOL, Msg.OK + t("Verteilen", "Hand out"), List.of(
                "<gray>" + t("Noch frei: ", "Left to give: ") + Msg.HL + handout.remaining(),
                "<dark_gray>" + t("Der Rest geht zufällig raus.", "The rest goes out at random."))), click -> {
            viewer.closeInventory();
            rewards.finishHandout(handout);
        });
    }
}
