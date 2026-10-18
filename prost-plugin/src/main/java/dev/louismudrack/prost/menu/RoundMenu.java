package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.game.ChallengeRun;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Pace;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.ui.Fmt;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /prost status: members, the running challenge, rules, your switches, the host's controls (54 slots) */
public final class RoundMenu extends Menu {
    private static final int[] MEMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19};

    private final Round round;

    public RoundMenu(ProstPlugin plugin, Player viewer, Round round) {
        super(plugin, viewer);
        this.round = round;
    }

    @Override protected int size() { return 54; }
    @Override protected String title() { return text("menu.round.title", V.of("code", round.code)); }
    @Override protected Menu back() { return new MainMenu(plugin, viewer); }

    @Override
    protected void draw() {
        boolean host = round.host.equals(viewer.getUniqueId());
        int i = 0;
        for (Member m : round.members.values()) {
            if (i >= MEMBER_SLOTS.length) break;
            List<String> lore = new ArrayList<>();
            lore.add(m.water ? text("menu.round.drunk_water", V.of("drunk", m.tally.drunk), V.of("water", m.tally.water))
                    : text("menu.round.drunk", V.of("drunk", m.tally.drunk)));
            lore.add(text("menu.round.limit_joker", V.of("limit", m.limit), V.of("jokers", m.jokers)));
            lore.add(state(m));
            if (host && !m.id.equals(viewer.getUniqueId())) lore.add(text("menu.round.click_remove"));
            Member target = m;
            set(MEMBER_SLOTS[i++], Items.head(m.id, (m.id.equals(round.host) ? "<gold>★ " : "") + text("menu.round.member", V.of("name", m.name)), lore),
                    host && !m.id.equals(viewer.getUniqueId()) ? e -> confirmKick(target) : null);
        }
        ChallengeRun run = plugin.director().running(round);
        List<String> now = run == null
                ? List.of(text("menu.round.next_in", V.of("time", Fmt.clock(Math.max(0, round.nextDrawAt - System.currentTimeMillis())))))
                : List.of(text("menu.round.run_text", V.of("text", run.def.text(lang))),
                        text("menu.round.run_info", V.mm("kind", text("kind." + run.kind.key)), V.of("time", Fmt.clock(run.remainingMs())),
                                V.mm("sips", Msg.sips(viewer.getUniqueId(), run.def.sips()))));
        set(28, Items.item(Material.MAP, run == null ? text("menu.round.no_challenge") : text("menu.round.challenge_name", V.of("name", run.def.name(lang))), now), null);
        List<String> rules = new ArrayList<>();
        for (Rule r : round.rules) rules.add(text("menu.round.rule_entry", V.of("text", r.text(lang))));
        if (rules.isEmpty()) rules.add(text("menu.round.rules_none"));
        set(30, Items.item(Material.WRITABLE_BOOK, "<hl>" + text("menu.round.rules"), rules), null);
        Member me = round.member(viewer.getUniqueId());
        if (me != null) {
            set(32, Items.item(me.selfPaused ? Material.LIME_DYE : Material.GRAY_DYE, "<hl>" + text("menu.round.pause"),
                    List.of(me.selfPaused ? text("menu.round.is_paused") : text("menu.round.is_playing"))), e -> command("prost pause"));
            set(33, Items.item(Material.COMPARATOR, "<hl>" + text("menu.round.limit_water"), lines("menu.round.limit_lore", V.of("limit", me.limit))),
                    e -> new JoinMenu(plugin, viewer, round).open());
            set(34, Items.item(Material.RED_DYE, "<err>" + text("menu.round.leave"), List.of()), e -> command("prost verlassen"));
        }
        if (host) {
            set(38, Items.item(Material.CLOCK, "<hl>" + text("menu.round.next_now"), List.of()), e -> command("prost weiter"));
            set(39, Items.item(round.paused ? Material.LIME_DYE : Material.GRAY_DYE, "<hl>" + text("menu.round.pause_round"),
                    List.of(round.paused ? text("menu.round.is_paused") : text("menu.round.is_running"))), e -> command("prost pause runde"));
            Pace nextPace = Pace.values()[(round.pace.ordinal() + 1) % Pace.values().length];
            set(40, Items.item(Material.REPEATER, "<hl>" + text("menu.round.pace", V.mm("pace", text("pace." + round.pace.key))),
                    lines("menu.round.pace_lore", V.mm("pace", text("pace." + nextPace.key)))), e -> command("prost tempo " + nextPace.key));
            set(41, Items.item(Material.TNT, "<err>" + text("menu.round.end"), List.of()), e -> confirmEnd());
        }
    }

    private String state(Member m) {
        if (m.away) return text("menu.round.state_away");
        if (m.afk) return text("menu.round.state_afk");
        if (m.selfPaused || round.paused) return text("menu.round.state_paused");
        if (m.wrongMode) return text("menu.round.state_wrong_mode");
        return text("menu.round.state_active");
    }

    /** the command does the checks and messages; the menu refreshes after it */
    private void command(String line) {
        viewer.performCommand(line);
        if (plugin.rounds().of(viewer.getUniqueId()) == round) render();
        else viewer.closeInventory();
    }

    private void confirmKick(Member target) {
        new ChoiceMenu(plugin, viewer, "menu.choice.remove", List.of(
                new ChoiceMenu.Option(Items.item(Material.LIME_WOOL, "<ok>" + text("menu.round.yes_remove"), List.of()), () -> viewer.performCommand("prost kick " + target.name)),
                new ChoiceMenu.Option(Items.item(Material.RED_WOOL, "<err>" + text("menu.round.cancel"), List.of()), () -> new RoundMenu(plugin, viewer, round).open()))).open();
    }

    private void confirmEnd() {
        new ChoiceMenu(plugin, viewer, "menu.choice.end", List.of(
                new ChoiceMenu.Option(Items.item(Material.LIME_WOOL, "<ok>" + text("menu.round.yes_end"), List.of()), () -> viewer.performCommand("prost ende")),
                new ChoiceMenu.Option(Items.item(Material.RED_WOOL, "<err>" + text("menu.round.cancel"), List.of()), () -> new RoundMenu(plugin, viewer, round).open()))).open();
    }
}
