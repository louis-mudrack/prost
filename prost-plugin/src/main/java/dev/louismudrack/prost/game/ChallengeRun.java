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
import dev.louismudrack.prost.reward.Rewards;
import dev.louismudrack.prost.storage.StatsField;
import dev.louismudrack.prost.tracker.GameTracker;
import dev.louismudrack.prost.tracker.TrackContext;
import dev.louismudrack.prost.tracker.Tracker;
import dev.louismudrack.prost.tracker.Trackers;
import dev.louismudrack.prost.tracker.Verdict;
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
import java.util.function.Function;

/**
 * One running challenge: announces it with its fixed sips, shows a boss bar to the whole round, polls the
 * tracker every 10 ticks, ends early when decided, then resolves: drinks through Sips, rewards through Rewards (only
 * when nobody drinks). A game tracker (GameTracker) decides the end and the verdict itself; the run keeps the HUD,
 * the drinks, the rewards and the stats.
 * A participant who stops being active drops out without drinking; a Duel opponent dropping out = walkover.
 */
public final class ChallengeRun {
    /** a watcher's boss bar lists at most this many players, then "+N" */
    private static final int WATCH_MAX = 3;
    private static final String HIDDEN_CLOCK = "??";

    private final ProstPlugin plugin;
    final Round round;
    public final ChallengeDef def;
    public final Kind kind;
    private final List<UUID> participants;
    private final Tracker tracker;
    /** null for the progress trackers */
    private final GameTracker game;
    private final long startedAt;
    /** a game may move it */
    private long endsAt;
    private final Map<UUID, Long> doneAt = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private boolean over, released;

    ChallengeRun(ProstPlugin plugin, Round round, ChallengeDef def, Kind kind, List<UUID> participants) {
        this(plugin, round, def, kind, participants, Trackers::create);
    }

    /** trackers: the entry's tracker; tests hand in a scripted one */
    ChallengeRun(ProstPlugin plugin, Round round, ChallengeDef def, Kind kind, List<UUID> participants, Function<TrackContext, Tracker> trackers) {
        this.plugin = plugin;
        this.round = round;
        this.def = def;
        this.kind = kind;
        this.participants = new ArrayList<>(participants);
        this.tracker = trackers.apply(new TrackContext(plugin, round, def, kind));
        this.game = tracker instanceof GameTracker g ? g : null;
        this.startedAt = plugin.now();
        this.endsAt = startedAt + def.seconds() * 1000L;
    }

    boolean involves(UUID id) { return participants.contains(id); }

    public long endsAt() { return endsAt; }

    public List<UUID> participants() { return List.copyOf(participants); }

    public long remainingMs() { return Math.max(0, endsAt - plugin.now()); }

    /** the time left as the players see it: "??" while the game hides it */
    public String clock() { return hiddenClock() ? HIDDEN_CLOCK : Fmt.clock(remainingMs()); }

    /** null for the progress trackers */
    public GameTracker game() { return game; }

    private boolean hiddenClock() { return game != null && game.hiddenClock(); }

    void start() {
        tracker.start(online());
        if (game != null) endsAt = game.deadline();
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
        long now = plugin.now();
        if (game != null) {
            tickGame(now);
            return;
        }
        for (UUID id : participants) if (!doneAt.containsKey(id) && done(id)) doneAt.put(id, now);
        update(now);
        if (now >= endsAt || decided()) finish();
    }

    private void tickGame(long now) {
        String cancelKey = game.cancelKey();
        if (cancelKey != null) {
            cancel(cancelKey);
            return;
        }
        endsAt = game.deadline();
        update(now);
        if (now >= endsAt || game.decided()) finish();
    }

    /** paused, AFK, away, wrong mode or left: out without drinking; still in the round = watches on */
    void drop(UUID id) {
        if (over || !participants.remove(id)) return;
        doneAt.remove(id);
        if (game != null) game.dropped(id);
        if (participants.isEmpty()) cancel(null);
        else if (kind == Kind.DUEL && participants.size() == 1) finish();
    }

    /** {@code key} is told to the round; null = silent */
    void cancel(String key) {
        if (over) return;
        over = true;
        end(key);
    }

    /** cancelled or broken: nobody drinks, and the round is free for the next challenge whatever throws on the way */
    private void end(String key) {
        try {
            release();
            if (key != null) Msg.round(round, key);
            Bukkit.getPluginManager().callEvent(new ChallengeEndEvent(Views.round(round, this), Views.challenge(this), false, List.of(), Map.of()));
        } finally {
            plugin.director().ended(round, false);
        }
    }

    /**
     * onDisable / round gone: stop listening and hide the bars, no scheduling; no messages of its own, but a vote tour it
     * stops brings its players back and tells them so (game.vote.returned)
     */
    void stopQuietly() {
        if (over) return;
        over = true;
        release();
    }

    /** once: listeners, menus and a tour end with the tracker, the bars with the run - also when stopping the tracker throws */
    private void release() {
        if (released) return;
        released = true;
        try {
            tracker.stop();
        } finally {
            hideBars();
        }
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
            case REACTION, LUCK, VOTE -> throw new IllegalStateException("decided by its game");
        };
    }

    /**
     * Judges, then pays out. A throw while judging (a game's verdict) cancels the run as an error - nobody drinks - and is
     * thrown on for the Director to log; the round draws its next challenge either way.
     */
    void finish() {
        if (over) return;
        over = true;
        Judged judged;
        try {
            judged = judge();
        } catch (RuntimeException e) {
            try {
                end("challenge.error");
            } catch (RuntimeException again) {
                e.addSuppressed(again);
            }
            throw e;
        }
        try {
            release();
            payOut(judged);
        } finally {
            plugin.director().ended(round, true);
        }
    }

    /** walkover: a Duel whose opponent dropped out, won without a game */
    private record Judged(Resolution.Outcome outcome, Map<UUID, Reward> forced, int sum, int target, boolean walkover) {}

    private Judged judge() {
        tracker.poll(online());
        int target = teamTarget(), sum = teamSum();
        if (kind == Kind.DUEL && participants.size() == 1)
            return new Judged(new Resolution.Outcome(Map.of(), List.copyOf(participants), null, true), Map.of(), sum, target, true);
        if (game != null) {
            Verdict v = game.verdict();
            return new Judged(new Resolution.Outcome(v.drinks(), v.winners(), null, !v.winners().isEmpty()), v.forced(), sum, target, false);
        }
        long now = plugin.now();
        for (UUID id : participants) if (!doneAt.containsKey(id) && done(id)) doneAt.put(id, now);
        List<Resolution.Progress> ps = new ArrayList<>();
        for (UUID id : participants) ps.add(new Resolution.Progress(id, tracker.progress(id), done(id), tracker.broken(id), doneAt.getOrDefault(id, 0L)));
        Collections.shuffle(ps);
        return new Judged(Resolution.resolve(kind, def.avoidType(), def.sips(), target, ps), Map.of(), sum, target, false);
    }

    private void payOut(Judged judged) {
        Resolution.Outcome o = judged.outcome();
        announceResult(o, judged.sum(), judged.target(), judged.walkover());
        for (UUID id : participants) {
            Member m = round.member(id);
            if (m == null) continue;
            boolean won = o.winners().contains(id), lost = lost(o, id);
            if (won) m.tally.won++;
            if (lost) m.tally.lost++;
            if (won || lost) plugin.stats().add(id, m.name, won ? StatsField.WON : StatsField.LOST, 1);
        }
        o.drinks().forEach((id, n) -> plugin.sips().drink(round, id, n, SipLedger.Source.CHALLENGE, viewer -> Msg.esc(def.name(Msg.lang(viewer)))));
        if (kind == Kind.TEAM) {
            if (!o.rewarded().isEmpty()) plugin.rewards().grant(round, o.mvp(), def, true, o.winners());
        } else {
            for (UUID w : o.rewarded()) {
                Reward given = judged.forced().get(w);
                if (given != null) plugin.rewards().give(round, w, def, given);
                else plugin.rewards().grant(round, w, def, false, List.of());
            }
        }
        Bukkit.getPluginManager().callEvent(new ChallengeEndEvent(Views.round(round, this), Views.challenge(this), true, o.winners(), o.drinks()));
    }

    // ---------------------------------------------------------------------------------------------- text

    /** kind, name, goal and who plays, in the title and in chat: the name alone was easy to miss */
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
            Msg.tell(p, "challenge.time", V.of("time", hiddenClock() ? HIDDEN_CLOCK : Fmt.clock(def.seconds() * 1000L)), V.mm("stakes", stakes(id)));
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

    /** what is at stake, honest per kind (a game may word its own): rewards only come when nobody drinks (Resolution.Outcome#rewarded) */
    private String stakes(UUID viewer) {
        String own = game == null ? null : game.stakes(viewer);
        if (own != null) return own;
        V sips = V.mm("sips", Msg.sips(viewer, def.sips())), reward = V.mm("reward", Rewards.offered(viewer, def, kind == Kind.TEAM));
        return switch (kind) {
            case SOLO -> Msg.str(viewer, "challenge.stakes.solo", sips, reward);
            case EACH -> Msg.str(viewer, "challenge.stakes.each", sips, reward);
            case TEAM -> Msg.str(viewer, "challenge.stakes.team", sips, reward);
            case RACE -> Msg.str(viewer, "challenge.stakes.race", sips);
            case DUEL -> Msg.str(viewer, "challenge.stakes.duel", sips);
            case REACTION -> Msg.str(viewer, "challenge.stakes.reaction", sips);
            case LUCK -> Msg.str(viewer, "challenge.stakes.luck", sips);
            case VOTE -> Msg.str(viewer, "challenge.stakes.vote", sips);
        };
    }

    /** a walkover keeps the default line: a game's own line would tell a game nobody lost */
    private void announceResult(Resolution.Outcome o, int sum, int target, boolean walkover) {
        for (UUID id : round.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            String lang = Msg.lang(id);
            String custom = game == null || walkover ? null : game.result(id);
            Msg.tell(p, "result.line", V.of("name", def.name(lang)), V.mm("result", custom != null ? custom : resultLine(id, o, sum, target)));
            boolean won = o.winners().contains(id);
            if (!participants.contains(id) || !won && !lost(o, id)) continue;
            Hud.title(p, Msg.str(id, won ? "result.won" : "result.lost"), "<gray>" + Msg.esc(def.name(lang)));
            if (won) Sounds.win(p);
        }
    }

    private String resultLine(UUID viewer, Resolution.Outcome o, int sum, int target) {
        if (kind == Kind.TEAM) return o.success() ? Msg.str(viewer, "result.team_ok") : Msg.str(viewer, "result.team_missed", V.of("sum", sum), V.of("target", target));
        if (o.winners().isEmpty()) return Msg.str(viewer, "result.nobody");
        boolean noReward = o.rewarded().isEmpty();
        return Msg.str(viewer, "result.made_it", V.mm("winners", names(o.winners(), ", "))) + (noReward ? " " + Msg.str(viewer, "result.no_reward") : "");
    }

    /** a progress challenge has no draw; in a game who neither won nor drinks counts as neither */
    private boolean lost(Resolution.Outcome o, UUID id) { return game == null ? !o.winners().contains(id) : o.drinks().containsKey(id); }

    /** MiniMessage: each name escaped, the separator as it is (a translator may style challenge.versus) */
    private String names(List<UUID> ids, String separator) {
        StringJoiner j = new StringJoiner(separator);
        for (UUID id : ids) {
            Member m = round.member(id);
            j.add(Msg.esc(m == null ? "?" : m.name));
        }
        return j.toString();
    }

    /** trimmed: language files from 0.3.0 carry the spaces inside the value (" vs ") */
    private static String versus(UUID viewer) { return " " + Msg.str(viewer, "challenge.versus").trim() + " "; }

    // ---------------------------------------------------------------------------------------------- HUD

    /** every member gets a bar - participants their own progress, the others (watching) the players' progress */
    private void update(long now) {
        // a hidden clock keeps the bar full: a shrinking bar would tell the time
        float left = hiddenClock() ? 1f : Math.max(0f, Math.min(1f, (endsAt - now) / (float) (endsAt - startedAt)));
        String clock = hiddenClock() ? HIDDEN_CLOCK : Fmt.clock(endsAt - now);
        for (UUID id : new ArrayList<>(bars.keySet())) if (!round.members.containsKey(id)) hideBar(id);
        for (UUID id : round.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            BossBar bar = bars.computeIfAbsent(id, k -> BossBar.bossBar(Component.empty(), 1f, Hud.color(kind), BossBar.Overlay.PROGRESS));
            p.showBossBar(bar);   // no-op while shown; shows it again after a reconnect
            boolean playing = participants.contains(id);
            String status = playing ? "<white>" + progressText(id, id) : watchText(id);
            bar.name(Msg.mm("<hl>" + Msg.esc(def.name(Msg.lang(id))) + " <dark_gray>· " + status + " <dark_gray>· <gray>" + clock
                    + (playing ? "" : " " + Msg.str(id, "challenge.bar_watching"))));
            bar.progress(left);
            if (playing) Hud.actionBar(p, hint(id) + " <dark_gray>- <white>" + clock);
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

    /** the action bar's live hint: "Noch 3 - 0:42" */
    private String hint(UUID id) {
        if (game != null) return game.status(id, id);
        if (def.avoidType()) return Msg.str(id, tracker.broken(id) ? "challenge.hint.broken" : "challenge.hint.hold");
        if (tracker.broken(id)) return Msg.str(id, "challenge.hint.out");
        int missing = kind == Kind.TEAM ? teamTarget() - teamSum() : def.amount() - tracker.progress(id);
        if (missing <= 0 || doneAt.containsKey(id)) return Msg.str(id, "challenge.hint.done");
        return Msg.str(id, "challenge.hint.to_go", V.of("missing", missing));
    }

    /** how far {@code who} is, worded for {@code viewer} */
    private String progressText(UUID viewer, UUID who) {
        if (game != null) return game.status(viewer, who);
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
