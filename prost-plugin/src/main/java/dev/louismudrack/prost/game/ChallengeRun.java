package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.catalogue.Need;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.i18n.Msg;
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
    private static final String GRADIENT = "<gradient:#ffd94d:#ff8f3f>";
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
        update(startedAt);
    }

    void tick() {
        if (over) return;
        if (gateClosed()) {
            cancel("Der Nether oder das End wurde geschlossen - abgebrochen, niemand trinkt.",
                    "The Nether or the End was closed - cancelled, nobody drinks.");
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
        if (participants.isEmpty()) cancel(null, null);
        else if (kind == Kind.DUEL && participants.size() == 1) finish();
    }

    void cancel(String de, String en) {
        if (over) return;
        over = true;
        tracker.stop();
        hideBars();
        if (de != null) Msg.round(round, g -> g ? de : en);
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
        o.drinks().forEach((id, n) -> plugin.sips().drink(round, id, n, SipLedger.Source.CHALLENGE, def.name("de"), def.name("en")));
        if (kind == Kind.TEAM) {
            if (!o.rewarded().isEmpty()) plugin.rewards().grant(round, o.mvp(), def, true, o.winners());
        } else {
            for (UUID w : o.rewarded()) plugin.rewards().grant(round, w, def, false, List.of());
        }
        plugin.director().ended(round, true);
    }

    // ---------------------------------------------------------------------------------------------- text

    /** test game 2026-10-17: the old title showed only the name - kind, goal and who plays were easy to miss in chat */
    private void announce() {
        for (UUID id : round.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean de = Msg.de(id);
            String lang = Msg.lang(id);
            String headline = Hud.kindName(kind, de) + " · " + Msg.esc(def.name(lang));
            String goal = Msg.esc(def.text(lang)) + teamGoal(de);
            if (participants.contains(id)) Hud.announce(p, GRADIENT + headline, "<white>" + goal);
            else Hud.announce(p, GRADIENT + whoPlays(de, lang), "<white>" + goal + " <dark_gray>(" + (de ? "du schaust zu" : "you are watching") + ")");
            String who = kind == Kind.SOLO || kind == Kind.DUEL ? " <dark_gray>» " + Msg.HL + names(participants, kind == Kind.DUEL ? " vs " : ", ") : "";
            p.sendMessage(Msg.mm(Msg.PREFIX + GRADIENT + headline + "</gradient>" + who));
            p.sendMessage(Msg.mm(Msg.PREFIX + "<white>" + goal));
            p.sendMessage(Msg.mm(Msg.PREFIX + (de ? "Zeit: <white>" : "Time: <white>") + Fmt.clock(def.seconds() * 1000L) + " <dark_gray>· " + stakes(de)));
            Sounds.ding(p);
        }
    }

    private String teamGoal(boolean de) {
        return kind == Kind.TEAM ? (de ? " <dark_gray>(zusammen " + teamTarget() + ")" : " <dark_gray>(" + teamTarget() + " together)") : "";
    }

    /** the title for round members who do not play this one */
    private String whoPlays(boolean de, String lang) {
        return switch (kind) {
            case SOLO -> names(participants, ", ") + (de ? " spielt Solo" : " plays Solo");
            case DUEL -> names(participants, " vs ");
            default -> Hud.kindName(kind, de) + " · " + Msg.esc(def.name(lang));
        };
    }

    /** what is at stake, honest per kind: rewards only come when nobody drinks (Resolution.Outcome#rewarded) */
    private String stakes(boolean de) {
        String sips = Msg.ERR + Fmt.sips(def.sips(), de), reward = Msg.OK + rewardText(de);
        return switch (kind) {
            case SOLO -> (de ? "<gray>Verfehlt: " : "<gray>Miss it: ") + sips + " <dark_gray>· " + (de ? "<gray>Geschafft: " : "<gray>Make it: ") + reward;
            case EACH -> (de ? "<gray>Wer es verfehlt: " : "<gray>Miss it: ") + sips + " <dark_gray>· "
                    + (de ? "<gray>Schaffen es alle: " : "<gray>Everyone makes it: ") + reward;
            case TEAM -> (de ? "<gray>Verfehlt: alle " : "<gray>Miss it: everyone drinks ") + sips + " <dark_gray>· "
                    + (de ? "<gray>Geschafft, der Beste wählt: " : "<gray>Make it, the top player picks: ") + reward;
            case RACE -> (de ? "<gray>Der Langsamste trinkt " : "<gray>The slowest drinks ") + sips;
            case DUEL -> (de ? "<gray>Der Verlierer trinkt " : "<gray>The loser drinks ") + sips;
        };
    }

    private String rewardText(boolean de) {
        List<Reward> rewards = kind == Kind.TEAM ? RewardRules.teamOptions(def.rewards()) : def.rewards();
        StringJoiner j = new StringJoiner(de ? " oder " : " or ");
        for (Reward r : rewards) j.add(switch (r) {
            case HANDOUT -> de ? def.give() + " Schlucke verteilen" : "hand out " + def.give() + " sips";
            case RULE -> de ? "eine Regel" : "a rule";
            case JOKER -> kind == Kind.TEAM ? (de ? "ein Joker für jeden" : "a joker each") : "Joker";
            case NEXT -> de ? "die nächste Herausforderung wählen" : "pick the next challenge";
        });
        return j.toString();
    }

    private void announceResult(Resolution.Outcome o, int sum, int target) {
        String winners = names(o.winners(), ", ");
        boolean noReward = !o.winners().isEmpty() && o.rewarded().isEmpty();
        for (UUID id : round.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean de = Msg.de(id);
            String lang = Msg.lang(id);
            String line;
            if (kind == Kind.TEAM) line = o.success() ? Msg.OK + (de ? "Ziel erreicht!" : "Target reached!")
                    : Msg.ERR + (de ? "Ziel verfehlt (" + sum + "/" + target + ")." : "Target missed (" + sum + "/" + target + ").");
            else line = o.winners().isEmpty() ? Msg.ERR + (de ? "Niemand hat es geschafft." : "Nobody made it.")
                    : Msg.OK + (de ? "Geschafft: " : "Made it: ") + Msg.HL + winners
                    + (noReward ? " <dark_gray>(" + (de ? "keine Belohnung, es wird getrunken" : "no reward, someone drinks") + ")" : "");
            p.sendMessage(Msg.mm(Msg.PREFIX + "<gradient:#ffd94d:#ff8f3f>" + Msg.esc(def.name(lang)) + "</gradient> <dark_gray>» " + line));
            if (!participants.contains(id)) continue;
            boolean won = o.winners().contains(id);
            Hud.title(p, won ? Msg.OK + (de ? "Geschafft!" : "Made it!") : Msg.ERR + (de ? "Verloren" : "Lost"), "<gray>" + Msg.esc(def.name(lang)));
            if (won) Sounds.win(p);
        }
    }

    private String names(List<UUID> ids, String separator) {
        StringJoiner j = new StringJoiner(separator);
        for (UUID id : ids) {
            Member m = round.member(id);
            j.add(Msg.esc(m == null ? "?" : m.name));
        }
        return j.toString();
    }

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
            boolean de = Msg.de(id), playing = participants.contains(id);
            String lang = Msg.lang(id);
            String status = playing ? "<white>" + progressText(id, de) : watchText(de);
            bar.name(Msg.mm(Msg.HL + Msg.esc(def.name(lang)) + " <dark_gray>· " + status + " <dark_gray>· <gray>" + Fmt.clock(endsAt - now)
                    + (playing ? "" : " <dark_gray>(" + (de ? "zuschauen" : "watching") + ")")));
            bar.progress(left);
            if (playing) Hud.actionBar(p, hint(id, de) + " <dark_gray>- <white>" + Fmt.clock(endsAt - now));
        }
    }

    private String watchText(boolean de) {
        if (kind == Kind.TEAM && !def.avoidType()) return "<white>" + teamSum() + "/" + teamTarget();
        StringJoiner j = new StringJoiner(" <dark_gray>· ");
        for (int i = 0; i < participants.size(); i++) {
            if (i == WATCH_MAX) {
                j.add("<gray>+" + (participants.size() - WATCH_MAX));
                break;
            }
            UUID id = participants.get(i);
            Member m = round.member(id);
            j.add("<white>" + Msg.esc(m == null ? "?" : m.name) + " " + progressText(id, de));
        }
        return j.toString();
    }

    /** the action bar's live hint (spec §10.1): "Noch 3 - 0:42" */
    private String hint(UUID id, boolean de) {
        if (def.avoidType()) return tracker.broken(id) ? Msg.ERR + (de ? "Gebrochen" : "Broken") : Msg.OK + (de ? "Durchhalten" : "Hold on");
        if (tracker.broken(id)) return Msg.ERR + (de ? "Raus" : "Out");
        int missing = kind == Kind.TEAM ? teamTarget() - teamSum() : def.amount() - tracker.progress(id);
        if (missing <= 0 || doneAt.containsKey(id)) return Msg.OK + (de ? "Geschafft" : "Done");
        return de ? "<gray>Noch " + Msg.HL + missing : Msg.HL + missing + " <gray>to go";
    }

    private String progressText(UUID id, boolean de) {
        if (def.avoidType()) return tracker.broken(id) ? Msg.ERR + (de ? "gebrochen" : "broken") : Msg.OK + (de ? "hält" : "holding");
        if (tracker.broken(id)) return Msg.ERR + (de ? "raus" : "out");
        if (kind == Kind.TEAM) return teamSum() + "/" + teamTarget();
        if (doneAt.containsKey(id)) return Msg.OK + (de ? "geschafft" : "done");
        return Math.min(tracker.progress(id), def.amount()) + "/" + def.amount();
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
