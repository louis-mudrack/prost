package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Pace;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /prost: open a round, join one (or open your own), your stats (UX skeleton, 27 slots) */
public final class MainMenu extends Menu {
    public MainMenu(ProstPlugin plugin, Player viewer) { super(plugin, viewer); }

    @Override protected int size() { return 27; }
    @Override protected String title() { return t("Trinkspiel", "Drinking game"); }

    @Override
    protected void draw() {
        Round mine = plugin.rounds().of(viewer.getUniqueId());
        if (mine != null) {
            set(11, Items.item(Material.CLOCK, Msg.HL + t("Meine Runde ", "My round ") + mine.code, List.of("<gray>" + mine.members.size() + t(" Spieler", " players"))),
                    e -> new RoundMenu(plugin, viewer, mine).open());
        } else {
            set(11, Items.item(Material.LIME_DYE, Msg.OK + t("Runde eröffnen", "Open a round"), List.of("<gray>" + t("Du wirst Host", "You become the host"))),
                    e -> paces());
        }
        List<String> open = new ArrayList<>();
        for (Round r : plugin.rounds().all()) {
            Member host = r.member(r.host);
            open.add("<gray>" + r.code + " <dark_gray>· <white>" + (host == null ? "?" : Msg.esc(host.name)) + " <dark_gray>· <gray>" + r.members.size() + "/" + plugin.cfg().maxPlayers());
        }
        if (open.isEmpty()) open.add("<gray>" + t("Gerade läuft keine Runde", "No round is running"));
        set(13, Items.item(Material.PLAYER_HEAD, Msg.HL + t("Runde beitreten", "Join a round"), open), e -> rounds());
        set(15, Items.item(Material.BOOK, Msg.HL + t("Meine Statistik", "My stats"), List.of()),
                e -> StatsMenu.load(plugin, viewer, viewer.getUniqueId(), viewer.getName()));
    }

    private void paces() {
        List<ChoiceMenu.Option> list = new ArrayList<>();
        for (Pace p : Pace.values()) {
            int[] w = plugin.cfg().pace().get(p);
            list.add(new ChoiceMenu.Option(Items.item(Material.CLOCK, Msg.HL + p.key, List.of("<gray>" + t("alle ", "every ") + w[0] / 60 + "-" + w[1] / 60 + " min")),
                    () -> viewer.performCommand("prost start " + p.key)));
        }
        new ChoiceMenu(plugin, viewer, "Tempo wählen", "Pick the pace", list).open();
    }

    private void rounds() {
        List<ChoiceMenu.Option> list = new ArrayList<>();
        for (Round r : plugin.rounds().all()) {
            Member host = r.member(r.host);
            list.add(new ChoiceMenu.Option(Items.head(r.host, Msg.HL + r.code, List.of("<gray>Host: <white>" + (host == null ? "?" : Msg.esc(host.name)),
                    "<gray>" + r.members.size() + "/" + plugin.cfg().maxPlayers() + " · " + r.pace.key)), () -> viewer.performCommand("prost join " + r.code)));
        }
        if (list.isEmpty()) {
            Msg.error(viewer, "Gerade läuft keine Runde - eröffne doch eine!", "No round is running - open one!");
            return;
        }
        new ChoiceMenu(plugin, viewer, "Runde beitreten", "Join a round", list).open();
    }
}
