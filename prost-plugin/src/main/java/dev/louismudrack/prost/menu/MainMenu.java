package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Pace;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /prost: open a round, join one (or open your own), your stats (UX skeleton, 27 slots) */
public final class MainMenu extends Menu {
    public MainMenu(ProstPlugin plugin, Player viewer) { super(plugin, viewer); }

    @Override protected int size() { return 27; }
    @Override protected String title() { return text("menu.main.title"); }

    @Override
    protected void draw() {
        Round mine = plugin.rounds().of(viewer.getUniqueId());
        if (mine != null) {
            set(11, Items.item(Material.CLOCK, text("menu.main.mine", V.of("code", mine.code)), lines("menu.main.mine_lore", V.of("count", mine.members.size()))),
                    e -> new RoundMenu(plugin, viewer, mine).open());
        } else {
            set(11, Items.item(Material.LIME_DYE, "<ok>" + text("menu.main.open"), lines("menu.main.open_lore")),
                    e -> paces());
        }
        List<String> open = new ArrayList<>();
        for (Round r : plugin.rounds().all()) {
            Member host = r.member(r.host);
            open.add(text("menu.main.open_entry", V.of("code", r.code), V.of("host", host == null ? "?" : host.name),
                    V.of("count", r.members.size()), V.of("max", plugin.cfg().maxPlayers())));
        }
        if (open.isEmpty()) open.addAll(lines("menu.main.none"));
        set(13, Items.item(Material.PLAYER_HEAD, "<hl>" + text("menu.main.join"), open), e -> rounds());
        set(15, Items.item(Material.BOOK, "<hl>" + text("menu.main.stats"), List.of()),
                e -> StatsMenu.load(plugin, viewer, viewer.getUniqueId(), viewer.getName()));
    }

    private void paces() {
        List<ChoiceMenu.Option> list = new ArrayList<>();
        for (Pace p : Pace.values()) {
            int[] w = plugin.cfg().pace().get(p);
            list.add(new ChoiceMenu.Option(Items.item(Material.CLOCK, "<hl>" + text("pace." + p.key),
                    lines("menu.main.pace_lore", V.of("from", w[0] / 60), V.of("to", w[1] / 60))),
                    () -> viewer.performCommand("prost start " + p.key)));
        }
        new ChoiceMenu(plugin, viewer, "menu.choice.pace", list).open();
    }

    private void rounds() {
        List<ChoiceMenu.Option> list = new ArrayList<>();
        for (Round r : plugin.rounds().all()) {
            Member host = r.member(r.host);
            list.add(new ChoiceMenu.Option(Items.head(r.host, "<hl>" + r.code, List.of(text("menu.main.round_host", V.of("host", host == null ? "?" : host.name)),
                    text("menu.main.round_info", V.of("count", r.members.size()), V.of("max", plugin.cfg().maxPlayers()),
                            V.mm("pace", text("pace." + r.pace.key))))), () -> viewer.performCommand("prost join " + r.code)));
        }
        if (list.isEmpty()) {
            Msg.error(viewer, "menu.main.none_error");
            return;
        }
        new ChoiceMenu(plugin, viewer, "menu.choice.join", list).open();
    }
}
