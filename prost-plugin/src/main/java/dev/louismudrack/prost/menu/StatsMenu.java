package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Rounds;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.roulette.Roulette;
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
    @Override protected String title() { return t("Statistik", "Stats"); }
    @Override protected Menu back() { return new MainMenu(plugin, viewer); }

    @Override
    protected void draw() {
        List<String> lore = new ArrayList<>();
        if (row == null) lore.add("<gray>" + t("Noch nie mitgespielt", "Never played yet"));
        else {
            lore.add("<gray>" + t("Getrunken: ", "Drunk: ") + Msg.HL + row.drunk() + " <dark_gray>· <gray>" + t("Wasser: ", "Water: ") + Msg.HL + row.water());
            lore.add("<gray>" + t("Verteilt: ", "Handed out: ") + Msg.HL + row.given());
            lore.add("<gray>" + t("Siege: ", "Won: ") + Msg.HL + row.won() + " <dark_gray>· <gray>" + t("Niederlagen: ", "Lost: ") + Msg.HL + row.lost());
            lore.add("<gray>Roulette: " + Msg.HL + row.roulette() + " <dark_gray>· <gray>Joker: " + Msg.HL + row.jokers());
            lore.add("<gray>" + t("Regeln: ", "Rules: ") + Msg.HL + row.rules() + " <dark_gray>· <gray>" + t("Runden: ", "Rounds: ") + Msg.HL + row.rounds());
        }
        set(13, Items.head(target, Msg.HL + Msg.esc(name), lore), null);
        set(29, board(Material.HONEY_BOTTLE, t("Meiste Schlucke", "Most sips"), drunk, StatsField.DRUNK), null);
        set(31, board(Material.CAKE, t("Großzügigste", "Most generous"), given, StatsField.GIVEN), null);
        set(33, board(Material.GOLDEN_APPLE, t("Meiste Siege", "Most wins"), won, StatsField.WON), null);
    }

    private org.bukkit.inventory.ItemStack board(Material icon, String title, List<StatsRow> rows, StatsField field) {
        List<String> lore = new ArrayList<>();
        int place = 1;
        for (StatsRow r : rows) {
            int value = switch (field) {
                case DRUNK -> r.drunk();
                case GIVEN -> r.given();
                default -> r.won();
            };
            lore.add("<gray>" + place++ + ". <white>" + Msg.esc(r.name()) + " <dark_gray>· " + Msg.HL + value);
        }
        if (lore.isEmpty()) lore.add("<gray>" + t("Noch leer", "Empty so far"));
        return Items.item(icon, Msg.HL + title, lore);
    }
}
