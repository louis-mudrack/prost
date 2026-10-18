package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.reward.Rewards;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** spread a winner's sips over the eligible members: left-click +1, right-click -1, confirm */
public final class HandoutMenu extends Menu {
    private final Rewards rewards;
    private final Rewards.Handout handout;

    public HandoutMenu(ProstPlugin plugin, Player viewer, Rewards rewards, Rewards.Handout handout) {
        super(plugin, viewer);
        this.rewards = rewards;
        this.handout = handout;
    }

    public boolean shows(Rewards.Handout h) { return handout == h; }

    @Override protected int size() { return 27; }
    @Override protected String title() { return text("menu.handout.title"); }

    @Override
    protected void draw() {
        int slot = 9;
        for (Map.Entry<UUID, Integer> e : handout.room.entrySet()) {
            if (slot > 17) break;
            UUID id = e.getKey();
            Member m = handout.round.member(id);
            if (m == null) continue;
            int given = handout.assigned.getOrDefault(id, 0);
            set(slot++, Items.head(id, text("menu.handout.member", V.of("name", m.name)),
                    lines("menu.handout.member_lore", V.of("given", given), V.of("max", e.getValue()))), click -> {
                if (click.isLeftClick() && handout.remaining() > 0 && given < e.getValue()) handout.assigned.merge(id, 1, Integer::sum);
                else if (click.isRightClick() && given > 0) handout.assigned.merge(id, -1, Integer::sum);
                render();
            });
        }
        set(22, Items.item(Material.LIME_WOOL, "<ok>" + text("menu.handout.confirm"),
                lines("menu.handout.confirm_lore", V.of("left", handout.remaining()))), click -> {
            viewer.closeInventory();
            rewards.finishHandout(handout);
        });
    }
}
