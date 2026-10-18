package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.api.event.ChallengeEndEvent;
import dev.louismudrack.prost.api.event.ChallengeStartEvent;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.catalogue.Need;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.reward.Reward;
import dev.louismudrack.prost.reward.RewardRules;
import dev.louismudrack.prost.storage.StatsField;
import dev.louismudrack.prost.tracker.TrackContext;
import dev.louismudrack.prost.tracker.Tracker;
import dev.louismudrack.prost.tracker.Trackers;
import dev.louismudrack.prost.ui.Fmt;
import dev.louismudrack.prost.ui.Hud;
import dev.louismudrack.prost.ui.Sounds;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * One running challenge (spec §4.1): announces it with its fixed sips, shows a boss bar to the whole round, polls the
 * tracker every 10 ticks, ends early when decided, then resolves: drinks through Sips, rewards through Rewards (only
 * when nobody drinks).
 * A participant who stops being active drops out without drinking; a Duel opponent dropping out = walkover.
 */
public final class ChallengeRun {
    /** a watcher's boss bar lists at most this many players, then "+N" */
    private static final int WATCH_MAX = 3;

    private final ProstPlugin plugin;
    final Round round;
    public final ChallengeDef def;
    public final Kind kind;
    private final List<UUID> participants;
    private final Tracker tracker;
    private final long startedAt, endsAt;
    private final Map<UUID, Long> doneAt = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private boolean over;

    ChallengeRun(ProstPlugin plugin, Round round, ChallengeDef def, Kind kind, List<UUID> participants) {
        this.plugin = plugin;
        this.round = round;
        this.def = def;
        this.kind = kind;
        this.participants = new ArrayList<>(participants);
        this.tracker = Trackers.create(new TrackContext(plugin, round, def));
        this.startedAt = System.currentTimeMillis();
        this.endsAt = startedAt + def.seconds() * 1000L;
    }

    boolean involves(UUID id) { return participants.contains(id); }

    public long endsAt() { return endsAt; }

    public List<UUID> participants() { return List.copyOf(participants); }

    public long remainingMs() { return Math.max(0, endsAt - System.currentTimeMillis()); }

    void start() {
        tracker.start(online());
        announce();
        Bukkit.getPluginManager().callEvent(new ChallengeStartEvent(Views.round(round, this), Views.challenge(this)));
        update(startedAt);
    }

    void tick() {
        if (over) return;
        if (gateClosed()) {
            cancel("challenge.gate_closed");
            return;
        }
        for (UUID id : new ArrayList<>(participants)) if (!round.isActive(id)) drop(id);
        if (over) return;
        tracker.poll(online());
        long now = System.currentTimeMillis();
        for (UUID id : participants) if (!doneAt.containsKey(id) && done(id)) doneAt.put(id, now);
        update(now);
        if (now >= endsAt || decided()) finish();
    }

    /** paused, AFK, away, wrong mode or left: out without drinking (spec §3.3); still in the round = watches on */
    void drop(UUID id) {
        if (over || !participants.remove(id)) return;
        doneAt.remove(id);
        if (participants.isEmpty()) cancel(null);
        else if (kind == Kind.DUEL && participants.size() == 1) finish();
    }

    /** {@code key} is told to the round; null = silent */
    void cancel(String key) {
        if (over) return;
        over = true;
        tracker.stop();
        hideBars();
        if (key != null) Msg.round(round, key);
        Bukkit.getPluginManager().callEvent(new ChallengeEndEvent(Views.round(round, this), Views.challenge(this), false, List.of(), Map.of()));
        plugin.director().ended(round, false);
    }

    /** onDisable / round gone: stop listening and hide the bars, no messages, no scheduling */
    void stopQuietly() {
        if (over) return;
        over = true;
        tracker.stop();
        hideBars();
    }

    private boolean gateClosed() {
        return (def.needs().contains(Need.NETHER) && !plugin.hooks().open(World.Environment.NETHER)) || (def.needs().contains(Need.END) && !plugin.hooks().open(World.Environment.THE_END));
    }

    private boolean done(UUID id) {
        boolean broken = tracker.broken(id);
        return def.avoidType() ? !broken : tracker.progress(id) >= def.amount() && !broken;
    }

    private int teamTarget() { return def.amount() * participants.size(); }

    private int teamSum() { return participants.stream().mapToInt(id -> tracker.progress(id)).sum(); }

    private boolean decided() {
        if ("inventory".equals(def.tracker())) return false;   // decided by what is held when the time runs out
        boolean anyBroken = participants.stream().anyMatch(id -> tracker.broken(id));
        boolean allSettled = participants.stream().allMatch(id -> doneAt.containsKey(id) || tracker.broken(id));
        return switch (kind) {
            case SOLO -> def.avoidType() ? anyBroken : allSettled;
            case EACH -> def.avoidType() ? participants.stream().allMatch(id -> tracker.broken(id)) : allSettled;
            case TEAM -> def.avoidType() ? anyBroken : teamSum() >= teamTarget();
            case RACE, DUEL -> !doneAt.isEmpty();
        };
    }

    void finish() {
        if (over) return;
        over = true;
        tracker.poll(online());
        long now = System.currentTimeMillis();
        for (UUID id : participants) if (!doneAt.containsKey(id) && done(id)) doneAt.put(id, now);
        int target = teamTarget(), sum = teamSum();
        Resolution.Outcome o;
        if (kind == Kind.DUEL && participants.size() == 1) {
            o = new Resolution.Outcome(Map.of(), List.copyOf(participants), null, true);
        } else {
            List<Resolution.Progress> ps = new ArrayList<>();
            for (UUID id : participants) ps.add(new Resolution.Progress(id, tracker.progress(id), done(id), tracker.broken(id), doneAt.getOrDefault(id, 0L)));
            Collections.shuffle(ps);
            o = Resolution.resolve(kind, def.avoidType(), def.sips(), target, ps);
        }
        tracker.stop();
        hideBars();
        announceResult(o, sum, target);
        for (UUID id : participants) {
            Member m = round.member(id);
            if (m == null) continue;
            boolean won = o.winners().contains(id);
            if (won) m.tally.won++;
            else m.tally.lost++;
            plugin.stats().add(id, m.name, won ? StatsField.WON : StatsField.LOST, 1);
        }
        o.drinks().forEach((id, n) -> plugin.sips().drink(round, id, n, SipLedger.Source.CHALLENGE, viewer -> Msg.esc(def.name(Msg.lang(viewer)))));
        if (kind == Kind.TEAM) {
            if (!o.rewarded().isEmpty()) plugin.rewards().grant(round, o.mvp(), def, true, o.winners());
        } else {
            for (UUID w : o.rewarded()) plugin.rewards().grant(round, w, def, false, List.of());
        }
        Bukkit.getPluginManager().callEvent(new ChallengeEndEvent(Views.round(round, this), Views.challenge(this), true, o.winners(), o.drinks()));
        plugin.director().ended(round, true);
    }

    // ---------------------------------------------------------------------------------------------- text

    /** test game 2026-10-17: the old title showed only the name - kind, goal and who plays were easy to miss in chat */
    private void announce() {
        for (UUID id : round.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            String lang = Msg.lang(id);
            String headline = kindAndName(id, lang);
            String goal = Msg.esc(def.text(lang)) + teamGoal(id);
            if (participants.contains(id)) Hud.announce(p, "<primary>" + headline + "</primary>", "<white>" + goal);
            else Hud.announce(p, "<primary>" + whoPlays(id, lang) + "</primary>", "<white>" + goal + " " + Msg.str(id, "challenge.watching"));
            String who = kind == Kind.SOLO || kind == Kind.DUEL ? " " + Msg.str(id, "challenge.who", V.mm("names", names(participants, kind == Kind.DUEL ? versus(id) : ", "))) : "";
            Msg.send(p, "<primary>" + headline + "</primary>" + who);
            Msg.send(p, "<white>" + goal);
            Msg.tell(p, "challenge.time", V.of("time", Fmt.clock(def.seconds() * 1000L)), V.mm("stakes", stakes(id)));
            Sounds.ding(p);
        }
    }

    private String kindAndName(UUID viewer, String lang) { return Msg.str(viewer, "kind." + kind.key) + " · " + Msg.esc(def.name(lang)); }

    private String teamGoal(UUID viewer) {
        return kind == Kind.TEAM ? " " + Msg.str(viewer, "challenge.team_goal", V.of("target", teamTarget())) : "";
    }

    /** the title for round members who do not play this one */
    private String whoPlays(UUID viewer, String lang) {
        return switch (kind) {
            case SOLO -> Msg.str(viewer, "challenge.solo_plays", V.mm("names", names(participants, ", ")));
            case DUEL -> names(participants, versus(viewer));
            default -> kindAndName(viewer, lang);
        };
    }

    /** what is at stake, honest per kind: rewards only come when nobody drinks (Resolution.Outcome#rewarded) */
    private String stakes(UUID viewer) {
        V sips = V.mm("sips", Msg.sips(viewer, def.sips())), reward = V.mm("reward", rewardText(viewer));
        return switch (kind) {
            case SOLO -> Msg.str(viewer, "challenge.stakes.solo", sips, reward);
            case EACH -> Msg.str(viewer, "challenge.stakes.each", sips, reward);
            case TEAM -> Msg.str(viewer, "challenge.stakes.team", sips, reward);
            case RACE -> Msg.str(viewer, "challenge.stakes.race", sips);
            case DUEL -> Msg.str(viewer, "challenge.stakes.duel", sips);
        };
    }

    private String rewardText(UUID viewer) {
        List<Reward> rewards = kind == Kind.TEAM ? RewardRules.teamOptions(def.rewards()) : def.rewards();
        StringJoiner j = new StringJoiner(Msg.str(viewer, "challenge.reward_or"));
        for (Reward r : rewards) j.add(switch (r) {
            case HANDOUT -> Msg.str(viewer, "challenge.reward.handout", V.mm("sips", Msg.sips(viewer, def.give())));
            case RULE -> Msg.str(viewer, "challenge.reward.rule");
            case JOKER -> Msg.str(viewer, kind == Kind.TEAM ? "challenge.reward.joker_each" : "challenge.reward.joker");
            case NEXT -> Msg.str(viewer, "challenge.reward.next");
        });
        return j.toString();
    }

    private void announceResult(Resolution.Outcome o, int sum, int target) {
        boolean noReward = !o.winners().isEmpty() && o.rewarded().isEmpty();
        for (UUID id : round.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            String lang = Msg.lang(id);
            String line;
            if (kind == Kind.TEAM) line = o.success() ? Msg.str(id, "result.team_ok") : Msg.str(id, "result.team_missed", V.of("sum", sum), V.of("target", target));
            else if (o.winners().isEmpty()) line = Msg.str(id, "result.nobody");
            else line = Msg.str(id, "result.made_it", V.mm("winners", names(o.winners(), ", "))) + (noReward ? " " + Msg.str(id, "result.no_reward") : "");
            Msg.tell(p, "result.line", V.of("name", def.name(lang)), V.mm("result", line));
            if (!participants.contains(id)) continue;
            boolean won = o.winners().contains(id);
            Hud.title(p, Msg.str(id, won ? "result.won" : "result.lost"), "<gray>" + Msg.esc(def.name(lang)));
            if (won) Sounds.win(p);
        }
    }

    /** the players' names, not escaped: callers insert them with V.of or Msg.esc */
    /** MiniMessage: each name escaped, the separator as it is (a translator may style challenge.versus) */
    private String names(List<UUID> ids, String separator) {
        StringJoiner j = new StringJoiner(separator);
        for (UUID id : ids) {
            Member m = round.member(id);
            j.add(Msg.esc(m == null ? "?" : m.name));
        }
        return j.toString();
    }

    private static String versus(UUID viewer) { return Msg.str(viewer, "challenge.versus"); }

    // ---------------------------------------------------------------------------------------------- HUD

    /** every member gets a bar - participants their own progress, the others (watching) the players' progress */
    private void update(long now) {
        float left = Math.max(0f, Math.min(1f, (endsAt - now) / (float) (endsAt - startedAt)));
        for (UUID id : new ArrayList<>(bars.keySet())) if (!round.members.containsKey(id)) hideBar(id);
        for (UUID id : round.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            BossBar bar = bars.computeIfAbsent(id, k -> BossBar.bossBar(Component.empty(), 1f, Hud.color(kind), BossBar.Overlay.PROGRESS));
            p.showBossBar(bar);   // no-op while shown; shows it again after a reconnect
            boolean playing = participants.contains(id);
            String status = playing ? "<white>" + progressText(id, id) : watchText(id);
            bar.name(Msg.mm("<hl>" + Msg.esc(def.name(Msg.lang(id))) + " <dark_gray>· " + status + " <dark_gray>· <gray>" + Fmt.clock(endsAt - now)
                    + (playing ? "" : " " + Msg.str(id, "challenge.bar_watching"))));
            bar.progress(left);
            if (playing) Hud.actionBar(p, hint(id) + " <dark_gray>- <white>" + Fmt.clock(endsAt - now));
        }
    }

    private String watchText(UUID viewer) {
        if (kind == Kind.TEAM && !def.avoidType()) return "<white>" + teamSum() + "/" + teamTarget();
        StringJoiner j = new StringJoiner(" <dark_gray>· ");
        for (int i = 0; i < participants.size(); i++) {
            if (i == WATCH_MAX) {
                j.add("<gray>+" + (participants.size() - WATCH_MAX));
                break;
            }
            UUID id = participants.get(i);
            Member m = round.member(id);
            j.add("<white>" + Msg.esc(m == null ? "?" : m.name) + " " + progressText(viewer, id));
        }
        return j.toString();
    }

    /** the action bar's live hint (spec §10.1): "Noch 3 - 0:42" */
    private String hint(UUID id) {
        if (def.avoidType()) return Msg.str(id, tracker.broken(id) ? "challenge.hint.broken" : "challenge.hint.hold");
        if (tracker.broken(id)) return Msg.str(id, "challenge.hint.out");
        int missing = kind == Kind.TEAM ? teamTarget() - teamSum() : def.amount() - tracker.progress(id);
        if (missing <= 0 || doneAt.containsKey(id)) return Msg.str(id, "challenge.hint.done");
        return Msg.str(id, "challenge.hint.to_go", V.of("missing", missing));
    }

    /** how far {@code who} is, worded for {@code viewer} */
    private String progressText(UUID viewer, UUID who) {
        if (def.avoidType()) return Msg.str(viewer, tracker.broken(who) ? "challenge.progress.broken" : "challenge.progress.holding");
        if (tracker.broken(who)) return Msg.str(viewer, "challenge.progress.out");
        if (kind == Kind.TEAM) return teamSum() + "/" + teamTarget();
        if (doneAt.containsKey(who)) return Msg.str(viewer, "challenge.progress.done");
        return Math.min(tracker.progress(who), def.amount()) + "/" + def.amount();
    }

    private void hideBar(UUID id) {
        BossBar bar = bars.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (bar != null && p != null) p.hideBossBar(bar);
    }

    private void hideBars() { for (UUID id : new ArrayList<>(bars.keySet())) hideBar(id); }

    private List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (UUID id : participants) { Player p = Bukkit.getPlayer(id); if (p != null) out.add(p); }
        return out;
    }
}
