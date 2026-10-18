package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Rounds;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.storage.StatsField;
import dev.louismudrack.prost.storage.StatsRow;
import dev.louismudrack.prost.storage.StatsStore;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** /prost stats [name]: one player's all-time numbers + three top-10 lists; data loads off the main thread first */
public final class StatsMenu extends Menu {
    private final UUID target;
    private final String name;
    private final StatsRow row;
    private final List<StatsRow> drunk, given, won;

    private StatsMenu(ProstPlugin plugin, Player viewer, UUID target, String name, StatsRow row, List<StatsRow> drunk, List<StatsRow> given, List<StatsRow> won) {
        super(plugin, viewer);
        this.target = target;
        this.name = name;
        this.row = row;
        this.drunk = drunk;
        this.given = given;
        this.won = won;
    }

    public static void load(ProstPlugin plugin, Player viewer, UUID target, String name) {
        StatsStore s = plugin.stats();
        s.row(target, row -> s.top(StatsField.DRUNK, drunk -> s.top(StatsField.GIVEN, given -> s.top(StatsField.WON, won -> {
            if (viewer.isOnline()) new StatsMenu(plugin, viewer, target, name, row, drunk, given, won).open();
        }))));
    }

    @Override protected int size() { return 54; }
    @Override protected String title() { return text("menu.stats.title"); }
    @Override protected Menu back() { return new MainMenu(plugin, viewer); }

    @Override
    protected void draw() {
        List<String> lore = row == null ? lines("menu.stats.never")
                : lines("menu.stats.numbers", V.of("drunk", row.drunk()), V.of("water", row.water()), V.of("given", row.given()),
                        V.of("won", row.won()), V.of("lost", row.lost()), V.of("roulette", row.roulette()), V.of("jokers", row.jokers()),
                        V.of("rules", row.rules()), V.of("rounds", row.rounds()));
        set(13, Items.head(target, text("menu.stats.player", V.of("name", name)), lore), null);
        set(29, board(Material.HONEY_BOTTLE, "menu.stats.most_sips", drunk, StatsField.DRUNK), null);
        set(31, board(Material.CAKE, "menu.stats.most_generous", given, StatsField.GIVEN), null);
        set(33, board(Material.GOLDEN_APPLE, "menu.stats.most_wins", won, StatsField.WON), null);
    }

    private org.bukkit.inventory.ItemStack board(Material icon, String titleKey, List<StatsRow> rows, StatsField field) {
        List<String> lore = new ArrayList<>();
        int place = 1;
        for (StatsRow r : rows) {
            int value = switch (field) {
                case DRUNK -> r.drunk();
                case GIVEN -> r.given();
                default -> r.won();
            };
            lore.add(text("menu.stats.entry", V.of("place", place++), V.of("name", r.name()), V.of("value", value)));
        }
        if (lore.isEmpty()) lore.addAll(lines("menu.stats.empty"));
        return Items.item(icon, "<hl>" + text(titleKey), lore);
    }
}
