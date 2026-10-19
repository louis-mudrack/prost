package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * A game's menu (luck, vote): 27 slots whose buttons the game draws, worded for the viewer, and draws again whenever the
 * game moves on. A click does not close it: the game decides what the menu shows next.
 */
public final class GameMenu extends Menu {
    private static final int SIZE = 27;

    private final String titleKey;
    private final Consumer<GameMenu> content;

    public GameMenu(ProstPlugin plugin, Player viewer, String titleKey, Consumer<GameMenu> content) {
        super(plugin, viewer);
        this.titleKey = titleKey;
        this.content = content;
    }

    @Override protected int size() { return SIZE; }
    @Override protected String title() { return text(titleKey); }
    @Override protected void draw() { content.accept(this); }

    /** a null action shows the icon only */
    public void button(int slot, ItemStack icon, Runnable action) { set(slot, icon, action == null ? null : e -> action.run()); }

    /** draws the content again while the menu is open; closed, it is drawn fresh on the next open */
    public void redraw() { if (isOpen()) render(); }
}
