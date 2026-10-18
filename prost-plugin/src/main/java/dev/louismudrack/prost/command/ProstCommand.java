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
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.JoinMenu;
import dev.louismudrack.prost.menu.MainMenu;
import dev.louismudrack.prost.menu.RoundMenu;
import dev.louismudrack.prost.menu.StatsMenu;
import dev.louismudrack.prost.roulette.Roulette;
import dev.louismudrack.prost.ui.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /prost (spec §3.1). Every reply in the player's language; console only gets the admin part. */
public final class ProstCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "prost.admin";
    private static final List<String> SUBS = List.of("start", "einladen", "join", "ablehnen", "verlassen", "pause", "status", "weiter",
            "tempo", "kick", "ende", "stats");

    private final ProstPlugin plugin;

    public ProstCommand(ProstPlugin plugin) { this.plugin = plugin; }

    /** a player, or the player an op runs it for: /execute as <player> run prost ... */
    static Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        if (s instanceof ProxiedCommandSender px && px.getCallee() instanceof Player p) return p;
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload") || sub.equals("admin")) {
            admin(sender, sub, args);
            return true;
        }
        Player p = player(sender);
        if (p == null) {
            sender.sendMessage("Players only (console: /prost reload, /prost admin ...).");
            return true;
        }
        Round r = plugin.rounds().of(p.getUniqueId());
        switch (sub) {
            case "" -> new MainMenu(plugin, p).open();
            case "start" -> start(p, r, args);
            case "einladen", "invite" -> invite(p, r, args);
            case "join" -> join(p, r, args);
            case "ablehnen", "decline" -> {
                plugin.rounds().decline(p.getUniqueId());
                Msg.tell(p, "command.declined");
            }
            case "verlassen", "leave" -> {
                if (r == null) notInRound(p);
                else plugin.rounds().leave(p.getUniqueId());
            }
            case "pause" -> pause(p, r, args);
            case "status" -> {
                if (r == null) notInRound(p);
                else new RoundMenu(plugin, p, r).open();
            }
            case "weiter", "next" -> next(p, r);
            case "tempo", "pace" -> pace(p, r, args);
            case "kick" -> kick(p, r, args);
            case "ende", "end" -> {
                if (host(p, r)) plugin.rounds().end(r);
            }
            case "stats" -> stats(p, args);
            case "joker" -> {
                if (!plugin.sips().useJoker(p)) Msg.error(p, "command.no_joker");
            }
            case "verteilen" -> {
                if (!plugin.rewards().reopenHandout(p)) Msg.error(p, "command.no_handout");
            }
            case "belohnung" -> {
                if (!plugin.rewards().reopenChoice(p)) Msg.error(p, "command.no_choice");
            }
            default -> help(p);
        }
        return true;
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
        plugin.rounds().invite(p, target, r);
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

    private void pause(Player p, Round r, String[] args) {
        if (r == null) { notInRound(p); return; }
        if (args.length > 1 && (args[1].equalsIgnoreCase("runde") || args[1].equalsIgnoreCase("round"))) {
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

    private void admin(CommandSender s, String sub, String[] args) {
        if (!s.hasPermission(ADMIN)) {
            Player p = player(s);
            if (p != null) Msg.error(p, "command.no_permission");
            return;
        }
        if (sub.equals("reload")) {
            plugin.reloadData();
            reply(s, "admin.reloaded", V.of("challenges", plugin.challenges().size()), V.of("rules", plugin.preparedRules().size()));
            return;
        }
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (what) {
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
            default -> reply(s, "admin.usage");
        }
    }

    /** a player (or the one an op runs it for) in their language, the console in English */
    private static void reply(CommandSender s, String key, V... vars) {
        Player p = player(s);
        if (p != null) Msg.tell(p, key, vars);
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
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        Player p = player(sender);
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(SUBS);
            if (sender.hasPermission(ADMIN)) { out.add("reload"); out.add("admin"); }
        } else if (args.length == 2 && p != null) {
            Round r = plugin.rounds().of(p.getUniqueId());
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "start", "tempo", "pace" -> { for (Pace pace : Pace.values()) out.add(pace.key); }
                case "pause" -> { if (r != null && r.host.equals(p.getUniqueId())) out.add("runde"); }
                case "einladen", "invite", "stats" -> { for (Player o : Bukkit.getOnlinePlayers()) if (!plugin.hooks().bot(o)) out.add(o.getName()); }
                case "join" -> { for (Round x : plugin.rounds().all()) out.add(x.code); }
                case "kick" -> { if (r != null) for (Member m : r.members.values()) out.add(m.name); }
                case "admin" -> out.addAll(List.of("list", "end", "force", "debug"));
                default -> { }
            }
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
