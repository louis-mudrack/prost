package dev.louismudrack.prost;

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

/** /prost (spec §3.1). Every reply DE/EN; console only gets the admin part. */
final class DrinksCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "prost.admin";
    private static final List<String> SUBS = List.of("start", "einladen", "join", "ablehnen", "verlassen", "pause", "status", "weiter",
            "tempo", "kick", "ende", "stats");

    private final ProstPlugin plugin;

    DrinksCommand(ProstPlugin plugin) { this.plugin = plugin; }

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
                Msg.tell(p, "Einladung abgelehnt.", "Invite declined.");
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
                if (!plugin.sips().useJoker(p)) Msg.error(p, "Gerade gibt es nichts, wofür du einen Joker einsetzen kannst.", "There is nothing to use a joker on right now.");
            }
            case "verteilen" -> {
                if (!plugin.rewards().reopenHandout(p)) Msg.error(p, "Du hast gerade keine Schlucke zu verteilen.", "You have no sips to hand out right now.");
            }
            case "belohnung" -> {
                if (!plugin.rewards().reopenChoice(p)) Msg.error(p, "Gerade wartet keine Wahl auf dich.", "No choice is waiting for you right now.");
            }
            default -> help(p);
        }
        return true;
    }

    private void start(Player p, Round r, String[] args) {
        if (r != null) {
            Msg.error(p, "Du bist schon in einer Runde (" + r.code + ").", "You are already in a round (" + r.code + ").");
            return;
        }
        Pace pace = args.length > 1 ? Pace.of(args[1]) : Pace.NORMAL;
        if (pace == null) {
            Msg.error(p, "Tempo: chill, normal oder party.", "Pace: chill, normal or party.");
            return;
        }
        Round nr = plugin.rounds().open(p, pace);
        Msg.tell(p, "Runde " + Msg.HL + nr.code + " <gray>eröffnet (Tempo " + pace.key + "). Lade Freunde ein: <white>/prost einladen <name> <gray>- oder sie kommen mit <white>/prost join " + nr.code,
                "Round " + Msg.HL + nr.code + " <gray>opened (pace " + pace.key + "). Invite friends: <white>/prost invite <name> <gray>- or they join with <white>/prost join " + nr.code);
        Sounds.success(p);
        new JoinMenu(plugin, p, nr).open();
    }

    private void invite(Player p, Round r, String[] args) {
        if (r == null) { notInRound(p); return; }
        if (args.length < 2) { Msg.error(p, "Nutzung: /prost einladen <name>", "Usage: /prost invite <name>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || Gate.bot(target)) { Msg.error(p, args[1] + " ist nicht online.", args[1] + " is not online."); return; }
        if (plugin.rounds().of(target.getUniqueId()) != null) { Msg.error(p, target.getName() + " spielt schon in einer Runde.", target.getName() + " is already in a round."); return; }
        plugin.rounds().invite(p, target, r);
        Msg.tell(p, "Einladung an " + Msg.HL + Msg.esc(target.getName()) + " <gray>geschickt.", "Invite sent to " + Msg.HL + Msg.esc(target.getName()) + "<gray>.");
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
            case ALREADY_IN_ROUND -> Msg.error(p, "Du bist schon in einer Runde (" + r.code + ").", "You are already in a round (" + r.code + ").");
            case NO_SUCH_ROUND -> Msg.error(p, "Diese Runde gibt es nicht.", "There is no such round.");
            case FULL -> Msg.error(p, "Die Runde ist voll.", "The round is full.");
        }
    }

    private void pause(Player p, Round r, String[] args) {
        if (r == null) { notInRound(p); return; }
        if (args.length > 1 && (args[1].equalsIgnoreCase("runde") || args[1].equalsIgnoreCase("round"))) {
            if (!host(p, r)) return;
            r.paused = !r.paused;
            boolean paused = r.paused;
            Msg.round(r, de -> paused ? (de ? "Die Runde ist pausiert." : "The round is paused.") : (de ? "Die Runde geht weiter." : "The round goes on."));
        } else {
            Member m = r.member(p.getUniqueId());
            m.selfPaused = !m.selfPaused;
            if (m.selfPaused) Msg.tell(p, "Du bist pausiert - keine Herausforderungen, kein Roulette, keine Schlucke.", "You are paused - no challenges, no roulette, no sips.");
            else Msg.tell(p, "Du spielst wieder mit.", "You are back in.");
        }
        plugin.rounds().save();
    }

    private void next(Player p, Round r) {
        if (!host(p, r)) return;
        if (plugin.director().next(r)) Msg.tell(p, "Die nächste Herausforderung kommt gleich.", "The next challenge is coming.");
        else Msg.error(p, "Es läuft gerade schon eine Herausforderung.", "A challenge is already running.");
    }

    private void pace(Player p, Round r, String[] args) {
        if (!host(p, r)) return;
        Pace pace = args.length > 1 ? Pace.of(args[1]) : null;
        if (pace == null) { Msg.error(p, "Tempo: chill, normal oder party.", "Pace: chill, normal or party."); return; }
        r.pace = pace;
        Msg.round(r, de -> (de ? "Tempo ist jetzt " : "Pace is now ") + Msg.HL + pace.key);
        plugin.rounds().save();
    }

    private void kick(Player p, Round r, String[] args) {
        if (!host(p, r)) return;
        Member target = args.length < 2 ? null : r.members.values().stream().filter(m -> m.name.equalsIgnoreCase(args[1])).findFirst().orElse(null);
        if (target == null || target.id.equals(p.getUniqueId())) { Msg.error(p, "Diesen Spieler gibt es in deiner Runde nicht.", "There is no such player in your round."); return; }
        Player t = Bukkit.getPlayer(target.id);
        if (t != null) Msg.tell(t, "Du wurdest aus der Runde entfernt.", "You were removed from the round.");
        plugin.rounds().leave(target.id);
    }

    private void stats(Player p, String[] args) {
        UUID id = p.getUniqueId();
        String name = p.getName();
        if (args.length > 1) {
            OfflinePlayer o = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (o == null) { Msg.error(p, "Diesen Spieler kenne ich nicht.", "I don't know that player."); return; }
            id = o.getUniqueId();
            name = o.getName() == null ? args[1] : o.getName();
        }
        StatsMenu.load(plugin, p, id, name);
    }

    private void admin(CommandSender s, String sub, String[] args) {
        if (!s.hasPermission(ADMIN)) {
            Player p = player(s);
            if (p != null) Msg.error(p, "Dafür fehlt dir die Berechtigung.", "You don't have permission for that.");
            return;
        }
        if (sub.equals("reload")) {
            plugin.reloadData();
            reply(s, "Neu geladen: " + plugin.challenges().size() + " Herausforderungen, " + plugin.preparedRules().size() + " Regeln.",
                    "Reloaded: " + plugin.challenges().size() + " challenges, " + plugin.preparedRules().size() + " rules.");
            return;
        }
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (what) {
            case "list" -> {
                if (plugin.rounds().all().isEmpty()) reply(s, "Keine Runde läuft.", "No round is running.");
                for (Round r : plugin.rounds().all()) {
                    ChallengeRun run = plugin.director().running(r);
                    reply(s, r.code + ": " + r.members.size() + " Spieler, Tempo " + r.pace.key + (run == null ? "" : ", läuft: " + run.def.id()),
                            r.code + ": " + r.members.size() + " players, pace " + r.pace.key + (run == null ? "" : ", running: " + run.def.id()));
                }
            }
            case "end" -> {
                Round r = args.length > 2 ? plugin.rounds().byCode(args[2]) : null;
                if (r == null) reply(s, "Unbekannter Code.", "Unknown code.");
                else plugin.rounds().end(r);
            }
            case "force" -> {
                Round r = args.length > 2 ? plugin.rounds().byCode(args[2]) : null;
                ChallengeDef d = args.length > 3 ? Catalogue.find(plugin.challenges(), args[3]) : null;
                Kind k = args.length > 4 ? Kind.of(args[4]) : null;
                if (r == null || d == null || k == null) reply(s, "Nutzung: /prost admin force <code> <id> <kind>", "Usage: /prost admin force <code> <id> <kind>");
                else if (!plugin.director().force(r, d, k)) reply(s, "Geht gerade nicht (läuft schon etwas, oder zu wenige aktive Spieler für diese Art).",
                        "Not possible now (something runs, or too few active players for that kind).");
            }
            case "debug" -> {
                Roulette rl = plugin.roulette();
                rl.debug = args.length > 2 ? args[2].equalsIgnoreCase("on") : !rl.debug;
                reply(s, "Roulette-Debug in der Konsole: " + (rl.debug ? "an" : "aus"), "Roulette debug in the console: " + (rl.debug ? "on" : "off"));
            }
            default -> reply(s, "/prost admin list | end <code> | force <code> <id> <kind> | debug [on|off]",
                    "/prost admin list | end <code> | force <code> <id> <kind> | debug [on|off]");
        }
    }

    private static void reply(CommandSender s, String de, String en) {
        Player p = player(s);
        if (p != null) Msg.tell(p, de, en);
        else s.sendMessage(en);
    }

    private boolean host(Player p, Round r) {
        if (r == null) { notInRound(p); return false; }
        if (r.host.equals(p.getUniqueId())) return true;
        Msg.error(p, "Das darf nur der Host.", "Only the host can do that.");
        return false;
    }

    private static void notInRound(Player p) { Msg.error(p, "Du bist in keiner Runde. Starte eine mit /prost start.", "You are not in a round. Start one with /prost start."); }

    private static void help(Player p) {
        Msg.tell(p, "<white>/prost <gray>Menü · <white>start <gray>· <white>einladen <name> <gray>· <white>join <code> <gray>· <white>pause [runde] <gray>· <white>weiter <gray>· <white>tempo <gray>· <white>kick <gray>· <white>ende <gray>· <white>verlassen <gray>· <white>stats",
                "<white>/prost <gray>menu · <white>start <gray>· <white>invite <name> <gray>· <white>join <code> <gray>· <white>pause [round] <gray>· <white>next <gray>· <white>pace <gray>· <white>kick <gray>· <white>end <gray>· <white>leave <gray>· <white>stats");
    }

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
                case "einladen", "invite", "stats" -> { for (Player o : Bukkit.getOnlinePlayers()) if (!Gate.bot(o)) out.add(o.getName()); }
                case "join" -> { for (Round x : plugin.rounds().all()) out.add(x.code); }
                case "kick" -> { if (r != null) for (Member m : r.members.values()) out.add(m.name); }
                case "admin" -> out.addAll(List.of("list", "end", "force", "debug"));
                default -> { }
            }
        }
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
