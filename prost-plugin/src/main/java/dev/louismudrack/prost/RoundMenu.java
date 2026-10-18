package dev.louismudrack.prost;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /prost status: members, the running challenge, rules, your switches, the host's controls (54 slots) */
final class RoundMenu extends Menu {
    private static final int[] MEMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19};

    private final Round round;

    RoundMenu(ProstPlugin plugin, Player viewer, Round round) {
        super(plugin, viewer);
        this.round = round;
    }

    @Override protected int size() { return 54; }
    @Override protected String title() { return t("Runde ", "Round ") + round.code; }
    @Override protected Menu back() { return new MainMenu(plugin, viewer); }

    @Override
    protected void draw() {
        boolean host = round.host.equals(viewer.getUniqueId());
        int i = 0;
        for (Member m : round.members.values()) {
            if (i >= MEMBER_SLOTS.length) break;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + t("Getrunken: ", "Drunk: ") + Msg.HL + m.tally.drunk + (m.water ? " <gray>(+" + m.tally.water + t(" Wasser)", " water)") : ""));
            lore.add("<gray>Limit: " + Msg.HL + m.limit + " <dark_gray>· <gray>Joker: " + Msg.HL + m.jokers);
            lore.add(state(m));
            if (host && !m.id.equals(viewer.getUniqueId())) lore.add("<dark_gray>" + t("Klick: entfernen", "Click: remove"));
            Member target = m;
            set(MEMBER_SLOTS[i++], Items.head(m.id, (m.id.equals(round.host) ? "<gold>★ " : "") + Msg.HL + Msg.esc(m.name), lore),
                    host && !m.id.equals(viewer.getUniqueId()) ? e -> confirmKick(target) : null);
        }
        ChallengeRun run = plugin.director().running(round);
        List<String> now = run == null
                ? List.of("<gray>" + t("Nächste in ", "Next in ") + Msg.HL + Fmt.clock(Math.max(0, round.nextDrawAt - System.currentTimeMillis())))
                : List.of("<white>" + Msg.esc(run.def.text(de)), "<gray>" + Hud.kindName(run.kind, de) + " <dark_gray>· <gray>" + Fmt.clock(run.remainingMs())
                        + " <dark_gray>· " + Msg.ERR + run.def.sips() + t(" Schlucke", " sips"));
        set(28, Items.item(Material.MAP, Msg.HL + (run == null ? t("Keine Herausforderung", "No challenge") : Msg.esc(run.def.name(de))), now), null);
        List<String> rules = new ArrayList<>();
        for (Rule r : round.rules) rules.add("<white>• " + Msg.esc(r.text(de)));
        if (rules.isEmpty()) rules.add("<gray>" + t("Noch keine", "None yet"));
        set(30, Items.item(Material.WRITABLE_BOOK, Msg.HL + t("Regeln", "Rules"), rules), null);
        Member me = round.member(viewer.getUniqueId());
        if (me != null) {
            set(32, Items.item(me.selfPaused ? Material.LIME_DYE : Material.GRAY_DYE, Msg.HL + t("Pause", "Pause"),
                    List.of(me.selfPaused ? Msg.OK + t("pausiert", "paused") : Msg.ERR + t("spielt", "playing"))), e -> command("prost pause"));
            set(33, Items.item(Material.COMPARATOR, Msg.HL + t("Limit & Wasser", "Limit & water"), List.of("<gray>" + me.limit + t(" pro Stunde", " per hour"))),
                    e -> new JoinMenu(plugin, viewer, round).open());
            set(34, Items.item(Material.RED_DYE, Msg.ERR + t("Runde verlassen", "Leave the round"), List.of()), e -> command("prost verlassen"));
        }
        if (host) {
            set(38, Items.item(Material.CLOCK, Msg.HL + t("Nächste Herausforderung jetzt", "Next challenge now"), List.of()), e -> command("prost weiter"));
            set(39, Items.item(round.paused ? Material.LIME_DYE : Material.GRAY_DYE, Msg.HL + t("Runde pausieren", "Pause the round"),
                    List.of(round.paused ? Msg.OK + t("pausiert", "paused") : Msg.ERR + t("läuft", "running"))), e -> command("prost pause runde"));
            Pace nextPace = Pace.values()[(round.pace.ordinal() + 1) % Pace.values().length];
            set(40, Items.item(Material.REPEATER, Msg.HL + t("Tempo: ", "Pace: ") + round.pace.key, List.of("<gray>" + t("Klick: ", "Click: ") + nextPace.key)),
                    e -> command("prost tempo " + nextPace.key));
            set(41, Items.item(Material.TNT, Msg.ERR + t("Runde beenden", "End the round"), List.of()), e -> confirmEnd());
        }
    }

    private String state(Member m) {
        if (m.away) return "<dark_gray>" + t("weg", "away");
        if (m.afk) return "<dark_gray>AFK";
        if (m.selfPaused || round.paused) return "<yellow>" + t("pausiert", "paused");
        if (m.wrongMode) return "<dark_gray>" + t("Kreativ/Zuschauer", "creative/spectator");
        return Msg.OK + t("aktiv", "active");
    }

    /** the command does the checks and messages; the menu refreshes after it */
    private void command(String line) {
        viewer.performCommand(line);
        if (plugin.rounds().of(viewer.getUniqueId()) == round) render();
        else viewer.closeInventory();
    }

    private void confirmKick(Member target) {
        new ChoiceMenu(plugin, viewer, "Entfernen?", "Remove?", List.of(
                new ChoiceMenu.Option(Items.item(Material.LIME_WOOL, Msg.OK + t("Ja, entfernen", "Yes, remove"), List.of()), () -> viewer.performCommand("prost kick " + target.name)),
                new ChoiceMenu.Option(Items.item(Material.RED_WOOL, Msg.ERR + t("Abbrechen", "Cancel"), List.of()), () -> new RoundMenu(plugin, viewer, round).open()))).open();
    }

    private void confirmEnd() {
        new ChoiceMenu(plugin, viewer, "Runde beenden?", "End the round?", List.of(
                new ChoiceMenu.Option(Items.item(Material.LIME_WOOL, Msg.OK + t("Ja, beenden", "Yes, end it"), List.of()), () -> viewer.performCommand("prost ende")),
                new ChoiceMenu.Option(Items.item(Material.RED_WOOL, Msg.ERR + t("Abbrechen", "Cancel"), List.of()), () -> new RoundMenu(plugin, viewer, round).open()))).open();
    }
}
