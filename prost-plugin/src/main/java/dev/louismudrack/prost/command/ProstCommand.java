package dev.louismudrack.prost.command;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.catalogue.Catalogue;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.game.ChallengeRun;
import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Pace;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.game.Rounds;
import dev.louismudrack.prost.i18n.Lang;
import dev.louismudrack.prost.i18n.LanguageResolver;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.JoinMenu;
import dev.louismudrack.prost.menu.MainMenu;
import dev.louismudrack.prost.menu.RoundMenu;
import dev.louismudrack.prost.menu.StatsMenu;
import dev.louismudrack.prost.roulette.Roulette;
import dev.louismudrack.prost.ui.Sounds;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;

/** /prost. Every reply in the player's language; console only gets the admin part, in English. */
public final class ProstCommand implements BasicCommand {
    private static final String PLAY = "prost.play";
    private static final String ADMIN = "prost.admin";
    private static final List<String> ADMIN_ARGS = List.of("reload", "list", "end", "force", "debug", "migrate");
    private static final String AUTO = "auto";
    // what chat buttons run: canonical names built here, so the V.of values inside a click argument are constants, never player text
    public static final String RUN_DECLINE = "/prost decline";
    public static final String RUN_REWARD = "/prost reward";
    public static final String RUN_HANDOUT = "/prost handout";
    public static final String RUN_JOKER = "/prost joker";

    private final ProstPlugin plugin;
    private volatile Subcommands subs;

    public ProstCommand(ProstPlugin plugin) { this.plugin = plugin; }

    /** the invite's accept button; round codes are generated letters and digits */
    public static String runJoin(String code) { return "/prost join " + code; }

    /** the subcommand names: English plus the language files' aliases, swapped on /prost reload */
    public void subcommands(Subcommands s) { subs = s; }

    @Override
    public boolean canUse(CommandSender sender) { return sender.hasPermission(PLAY); }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "" : subs.canonical(args[0]);
        if (sub.equals("reload") || sub.equals("admin")) {
            admin(sender, sub, args);
            return;
        }
        CommandSender actor = source.getExecutor() == null ? sender : source.getExecutor();   // /execute as <entity> run prost ...
        if (!(actor instanceof Player p)) {
            reply(sender, "command.players_only");
            return;
        }
        Round r = plugin.rounds().of(p.getUniqueId());
        switch (sub) {
            case "" -> new MainMenu(plugin, p).open();
            case "start" -> start(p, r, args);
            case "invite" -> invite(p, r, args);
            case "join" -> join(p, r, args);
            case "decline" -> decline(p);
            case "leave" -> leave(p, r);
            case "pause" -> pause(p, r, args);
            case "status" -> status(p, r);
            case "next" -> next(p, r);
            case "pace" -> pace(p, r, args);
            case "kick" -> kick(p, r, args);
            case "end" -> end(p, r);
            case "stats" -> stats(p, args);
            case "language" -> language(p, args);
            case "joker" -> joker(p);
            case "handout" -> handout(p);
            case "reward" -> reward(p);
            default -> help(p);
        }
    }

    private void start(Player p, Round r, String[] args) {
        if (r != null) {
            Msg.error(p, "round.already_in", V.of("code", r.code));
            return;
        }
        Pace pace = args.length > 1 ? Pace.of(args[1]) : Pace.NORMAL;
        if (pace == null) {
            Msg.error(p, "command.pace_usage");
            return;
        }
        Round nr = plugin.rounds().open(p, pace);
        Msg.tell(p, "round.opened", V.of("code", nr.code), V.mm("pace", Msg.str(p.getUniqueId(), "pace." + pace.key)));
        Sounds.success(p);
        new JoinMenu(plugin, p, nr).open();
    }

    private void invite(Player p, Round r, String[] args) {
        if (r == null) { notInRound(p); return; }
        if (args.length < 2) { Msg.error(p, "command.invite_usage"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || plugin.hooks().bot(target)) { Msg.error(p, "command.not_online", V.of("player", args[1])); return; }
        if (plugin.rounds().of(target.getUniqueId()) != null) { Msg.error(p, "command.already_playing", V.of("player", target.getName())); return; }
        if (!plugin.rounds().invite(p, target, r)) { Msg.error(p, "command.invite_pending", V.of("player", target.getName())); return; }
        Msg.tell(p, "command.invite_sent", V.of("player", target.getName()));
    }

    private void join(Player p, Round r, String[] args) {
        Round target;
        if (args.length > 1) target = plugin.rounds().find(args[1]);
        else {
            Rounds.Invite inv = plugin.rounds().invite(p.getUniqueId());
            target = inv == null ? null : plugin.rounds().byCode(inv.code());
        }
        switch (plugin.rounds().join(p, target)) {
            case OK -> {
                Sounds.success(p);
                new JoinMenu(plugin, p, target).open();
            }
            case ALREADY_IN_ROUND -> Msg.error(p, "round.already_in", V.of("code", r.code));
            case NO_SUCH_ROUND -> Msg.error(p, "round.no_such");
            case FULL -> Msg.error(p, "round.full");
        }
    }

    private void decline(Player p) {
        plugin.rounds().decline(p.getUniqueId());
        Msg.tell(p, "command.declined");
    }

    private void leave(Player p, Round r) {
        if (r == null) notInRound(p);
        else plugin.rounds().leave(p.getUniqueId());
    }

    private void pause(Player p, Round r, String[] args) {
        if (r == null) { notInRound(p); return; }
        if (args.length > 1 && subs.canonical(args[1]).equals("round")) {
            if (!host(p, r)) return;
            r.paused = !r.paused;
            Msg.round(r, r.paused ? "round.paused" : "round.resumed");
        } else {
            Member m = r.member(p.getUniqueId());
            m.selfPaused = !m.selfPaused;
            Msg.tell(p, m.selfPaused ? "round.self_paused" : "round.self_back");
        }
        plugin.rounds().save();
    }

    private void status(Player p, Round r) {
        if (r == null) notInRound(p);
        else new RoundMenu(plugin, p, r).open();
    }

    private void next(Player p, Round r) {
        if (!host(p, r)) return;
        if (plugin.director().next(r)) Msg.tell(p, "round.next_coming");
        else Msg.error(p, "round.next_running");
    }

    private void pace(Player p, Round r, String[] args) {
        if (!host(p, r)) return;
        Pace pace = args.length > 1 ? Pace.of(args[1]) : null;
        if (pace == null) { Msg.error(p, "command.pace_usage"); return; }
        r.pace = pace;
        Msg.roundEach(r, id -> Msg.str(id, "round.pace_now", V.mm("pace", Msg.str(id, "pace." + pace.key))));
        plugin.rounds().save();
    }

    private void kick(Player p, Round r, String[] args) {
        if (!host(p, r)) return;
        Member target = args.length < 2 ? null : r.members.values().stream().filter(m -> m.name.equalsIgnoreCase(args[1])).findFirst().orElse(null);
        if (target == null || target.id.equals(p.getUniqueId())) { Msg.error(p, "round.no_such_player"); return; }
        Player t = Bukkit.getPlayer(target.id);
        if (t != null) Msg.tell(t, "round.removed");
        plugin.rounds().leave(target.id);
    }

    private void end(Player p, Round r) {
        if (host(p, r)) plugin.rounds().end(r);
    }

    private void stats(Player p, String[] args) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        if (args.length > 1) {
            OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (o == null) { Msg.error(p, "command.unknown_player"); return; }
            id = o.getUniqueId();
            name = o.getName() == null ? args[1] : o.getName();
        }
        StatsMenu.load(plugin, p, id, name);
    }

    /** a language provider or language.per-player: false decides for everyone - a choice here would change nothing */
    private void language(Player p, String[] args) {
        Lang lang = plugin.lang();
        String available = String.join(", ", new TreeSet<>(lang.languages()));
        if (plugin.hooks().hasLanguageProvider() || !plugin.pluginConfig().perPlayerLanguage()) {
            Msg.tell(p, "language.managed");
            return;
        }
        if (args.length < 2) {
            Msg.tell(p, "language.current", V.of("language", Msg.lang(p.getUniqueId())), V.of("available", available));
            return;
        }
        String code = args[1].equalsIgnoreCase(AUTO) ? null : LanguageResolver.normalize(args[1]);
        if (code != null && !lang.has(code)) {
            Msg.error(p, "language.unknown", V.of("language", args[1]), V.of("available", available));
            return;
        }
        plugin.prefs().setLanguage(p, code);
        Msg.tell(p, "language.set", V.of("language", Msg.lang(p.getUniqueId())));
    }

    private void joker(Player p) {
        if (!plugin.sips().useJoker(p)) Msg.error(p, "command.no_joker");
    }

    private void handout(Player p) {
        if (!plugin.rewards().reopenHandout(p)) Msg.error(p, "command.no_handout");
    }

    private void reward(Player p) {
        if (!plugin.rewards().reopenChoice(p)) Msg.error(p, "command.no_choice");
    }

    /** /prost admin reload|list|end|force|debug|migrate; /prost reload stays as the short form */
    private void admin(CommandSender s, String sub, String[] args) {
        if (!s.hasPermission(ADMIN)) {
            if (s instanceof Player p) Msg.error(p, "command.no_permission");
            else reply(s, "command.no_permission");
            return;
        }
        String what = sub.equals("reload") ? "reload" : args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (what) {
            case "reload" -> {
                plugin.reloadData();
                reply(s, "admin.reloaded", V.of("challenges", plugin.challenges().size()), V.of("rules", plugin.preparedRules().size()));
            }
            case "list" -> {
                if (plugin.rounds().all().isEmpty()) reply(s, "admin.none");
                for (Round r : plugin.rounds().all()) {
                    ChallengeRun run = plugin.director().running(r);
                    V[] vars = {V.of("code", r.code), V.of("players", r.members.size()), V.of("pace", r.pace.key), V.of("id", run == null ? "" : run.def.id())};
                    reply(s, run == null ? "admin.list_idle" : "admin.list_running", vars);
                }
            }
            case "end" -> {
                Round r = args.length > 2 ? plugin.rounds().byCode(args[2]) : null;
                if (r == null) reply(s, "admin.unknown_code");
                else plugin.rounds().end(r);
            }
            case "force" -> {
                Round r = args.length > 2 ? plugin.rounds().byCode(args[2]) : null;
                ChallengeDef d = args.length > 3 ? Catalogue.find(plugin.challenges(), args[3]) : null;
                Kind k = args.length > 4 ? Kind.of(args[4]) : null;
                if (r == null || d == null || k == null) reply(s, "admin.force_usage");
                else if (!plugin.director().force(r, d, k)) reply(s, "admin.force_refused");
            }
            case "debug" -> {
                Roulette rl = plugin.roulette();
                rl.debug = args.length > 2 ? args[2].equalsIgnoreCase("on") : !rl.debug;
                reply(s, rl.debug ? "admin.debug_on" : "admin.debug_off");
            }
            case "migrate" -> {
                switch (plugin.migrate()) {
                    case DONE -> reply(s, "admin.migrated");
                    case NOTHING -> reply(s, "admin.nothing_to_migrate");
                    case BLOCKED -> reply(s, "admin.migrate_blocked");
                    case FAILED -> reply(s, "admin.migrate_failed");
                }
            }
            default -> reply(s, "admin.usage");
        }
    }

    /** a player in their language, the console in English */
    private static void reply(CommandSender s, String key, V... vars) {
        if (s instanceof Player p) Msg.tell(p, key, vars);
        else s.sendMessage(Msg.mm(Msg.render(Lang.FALLBACK, text -> text, key, vars)));
    }

    private boolean host(Player p, Round r) {
        if (r == null) { notInRound(p); return false; }
        if (r.host.equals(p.getUniqueId())) return true;
        Msg.error(p, "round.host_only");
        return false;
    }

    private static void notInRound(Player p) { Msg.error(p, "round.not_in"); }

    private static void help(Player p) { Msg.tell(p, "command.help"); }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender s = source.getSender();
        String lang = s instanceof Player p ? Msg.lang(p.getUniqueId()) : Lang.FALLBACK;
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = args.length <= 1 ? subs.visible(lang, s.hasPermission(ADMIN)) : args.length > 2 ? List.of() : switch (subs.canonical(args[0])) {
            case "start", "pace" -> Arrays.stream(Pace.values()).map(pace -> pace.key).toList();
            case "invite", "stats" -> onlineNames();
            case "kick" -> roundNames(s);
            case "join" -> joinTargets();
            case "pause" -> subs.namesOf("round", lang);
            case "language" -> languageOptions();
            case "admin" -> s.hasPermission(ADMIN) ? ADMIN_ARGS : List.<String>of();
            default -> List.<String>of();
        };
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().filter(o -> !plugin.hooks().bot(o)).map(Player::getName).toList();
    }

    /** the other members of the sender's round */
    private List<String> roundNames(CommandSender s) {
        if (!(s instanceof Player p)) return List.of();
        Round r = plugin.rounds().of(p.getUniqueId());
        if (r == null) return List.of();
        return r.members.values().stream().filter(m -> !m.id.equals(p.getUniqueId())).map(m -> m.name).toList();
    }

    /** /prost join takes a round code or its host's name */
    private List<String> joinTargets() {
        List<String> out = new ArrayList<>();
        for (Round r : plugin.rounds().all()) {
            out.add(r.code);
            Member host = r.member(r.host);
            if (host != null) out.add(host.name);
        }
        return out;
    }

    private List<String> languageOptions() {
        List<String> out = new ArrayList<>(new TreeSet<>(plugin.lang().languages()));
        out.add(AUTO);
        return out;
    }
}
