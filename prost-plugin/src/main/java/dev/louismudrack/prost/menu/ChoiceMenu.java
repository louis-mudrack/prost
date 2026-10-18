package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** pick one of a few options (reward, challenge card, player): centred, one click decides */
public final class ChoiceMenu extends Menu {
    public record Option(ItemStack icon, Runnable action) {}

    private static final int SPACED_MAX = 4;

    private final String titleKey;
    private final List<Option> options;

    public ChoiceMenu(ProstPlugin plugin, Player viewer, String titleKey, List<Option> options) {
        super(plugin, viewer);
        this.titleKey = titleKey;
        this.options = options;
    }

    @Override protected int size() { return 27; }
    @Override protected String title() { return text(titleKey); }

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
