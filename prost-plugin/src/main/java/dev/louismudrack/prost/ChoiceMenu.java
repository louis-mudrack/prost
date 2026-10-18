package dev.louismudrack.prost;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** pick one of a few options (reward, challenge card, player): centred, one click decides */
final class ChoiceMenu extends Menu {
    record Option(ItemStack icon, Runnable action) {}

    private static final int SPACED_MAX = 4;

    private final String titleDe, titleEn;
    private final List<Option> options;

    ChoiceMenu(ProstPlugin plugin, Player viewer, String titleDe, String titleEn, List<Option> options) {
        super(plugin, viewer);
        this.titleDe = titleDe;
        this.titleEn = titleEn;
        this.options = options;
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return t(titleDe, titleEn); }

    @Override
    protected void draw() {
        int n = Math.min(options.size(), 9);
        for (int i = 0; i < n; i++) {
            int slot = n <= SPACED_MAX ? 13 - (n - 1) + 2 * i : 9 + (9 - n) / 2 + i;
            Option o = options.get(i);
            set(slot, o.icon(), e -> {
                viewer.closeInventory();
                o.action().run();
            });
        }
    }
}
