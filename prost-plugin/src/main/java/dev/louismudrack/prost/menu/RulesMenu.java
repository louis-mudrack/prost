package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.i18n.V;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/**
 * Pick a prepared rule or write your own; with the list full, a second screen picks which rule goes.
 * 28 rules per page in the interior, prev/next in the nav row.
 */
public final class RulesMenu extends Menu {
    private static final int[] CONTENT = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final List<Rule> rules;
    private final Consumer<Integer> picked;
    private final Runnable ownRule;
    private int page;

    /** ownRule null = replace mode (no "own rule" button) */
    public RulesMenu(ProstPlugin plugin, Player viewer, List<Rule> rules, Consumer<Integer> picked, Runnable ownRule) {
        super(plugin, viewer);
        this.rules = rules;
        this.picked = picked;
        this.ownRule = ownRule;
    }

    @Override protected int size() { return 54; }
    @Override protected String title() { return text(ownRule == null ? "menu.rules.title_replace" : "menu.rules.title_pick"); }

    @Override
    protected void draw() {
        int from = page * CONTENT.length;
        for (int i = 0; i < CONTENT.length && from + i < rules.size(); i++) {
            int index = from + i;
            set(CONTENT[i], Items.item(Material.PAPER, text("menu.rules.entry", V.of("text", rules.get(index).text(lang))), List.of()), e -> {
                viewer.closeInventory();
                picked.accept(index);
            });
        }
        if (page > 0) set(46, Items.item(Material.PAPER, "<gray>" + text("menu.rules.previous"), List.of()), e -> { page--; render(); });
        if (from + CONTENT.length < rules.size()) set(52, Items.item(Material.PAPER, "<gray>" + text("menu.rules.next"), List.of()), e -> { page++; render(); });
        if (ownRule != null) set(49, Items.item(Material.WRITABLE_BOOK, "<hl>" + text("menu.rules.own"),
                lines("menu.rules.own_lore")), e -> {
            viewer.closeInventory();
            ownRule.run();
        });
    }
}
