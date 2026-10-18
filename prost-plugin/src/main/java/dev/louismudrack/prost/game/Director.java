package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.catalogue.Catalogue;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.catalogue.DrawRules;
import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.ui.Hud;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The game loop (spec §3.2, §4.1, §4.4): every 10 ticks it advances the running challenges and, per round, draws the
 * next one when its time has come - only what is possible right now, the winner's queued pick first. One run per
 * round, never two; rule reminders every rules.reminder-minutes.
 */
public final class Director {
    private static final long RETRY_MS = 60_000L, RESUME_MIN_WAIT_MS = 60_000L, MINUTE_MS = 60_000L;

    private final ProstPlugin plugin;
    private final Map<String, ChallengeRun> runs = new HashMap<>();
    private final Map<String, Boolean> wasPaused = new HashMap<>();
    private final Random rnd = new Random();

    public Director(ProstPlugin plugin) { this.plugin = plugin; }

    public void tick() {
        for (ChallengeRun run : new ArrayList<>(runs.values())) {
            if (plugin.rounds().byCode(run.round.code) == null) {   // the round ended mid-challenge
                run.stopQuietly();
                runs.remove(run.round.code);
                continue;
            }
            try {
                run.tick();
            } catch (RuntimeException e) {
                failed(run, e);
            }
        }
        long now = System.currentTimeMillis();
        for (Round r : new ArrayList<>(plugin.rounds().all())) {
            try {
                if (Boolean.TRUE.equals(wasPaused.put(r.code, r.paused)) && !r.paused)
                    r.nextDrawAt = DirectorRules.afterResume(now, r.nextDrawAt, RESUME_MIN_WAIT_MS);
                List<UUID> active = r.activeIds();
                if (DirectorRules.mayStart(runs.containsKey(r.code), r.paused, active.size(), now, r.nextDrawAt)) draw(r, active);
                remindRules(r, now);
                showPaused(r);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "round " + r.code + ": starting a challenge failed - next try in a minute", e);
                r.nextDrawAt = now + RETRY_MS;
            }
        }
    }

    /** one broken challenge (e.g. an admin-edited entry the server rejects) must not stop every other round */
    private void failed(ChallengeRun run, RuntimeException e) {
        plugin.getLogger().log(Level.WARNING, "challenge " + run.def.id() + " in round " + run.round.code + " failed - cancelled", e);
        try {
            run.cancel("Diese Herausforderung hatte einen Fehler - abgebrochen, niemand trinkt.",
                    "This challenge hit an error - cancelled, nobody drinks.");
        } catch (RuntimeException again) {
            ended(run.round, false);   // cancel broke halfway: the run is marked over, drop it anyway
        }
    }

    /** the action bar says so while you or the whole round is paused (spec §10.1) */
    private static void showPaused(Round r) {
        for (Member m : r.members.values()) {
            if (!m.selfPaused && !r.paused) continue;
            Player p = Bukkit.getPlayer(m.id);
            if (p != null) Hud.actionBar(p, "<yellow>" + Msg.t(m.id, "Trinkspiel pausiert", "Drinking game paused"));
        }
    }

    private void draw(Round r, List<UUID> active) {
        List<Player> players = new ArrayList<>();
        for (UUID id : active) { Player p = Bukkit.getPlayer(id); if (p != null) players.add(p); }
        if (players.isEmpty()) return;
        DrawRules.Conditions c = Situation.of(plugin.hooks(), players);
        DrawRules.Choice choice = null;
        List<UUID> chosen = List.of();
        Round.Queued q = r.queued;
        r.queued = null;
        if (q != null) {
            ChallengeDef d = Catalogue.find(plugin.challenges(), q.id());
            if (d != null && DrawRules.feasible(d, c) && DrawRules.kinds(d, active.size()).contains(q.kind()) && active.containsAll(q.players())) {
                choice = new DrawRules.Choice(d, q.kind());
                chosen = q.players();
            }
        }
        if (choice == null) {
            DrawRules.Draw draw = DrawRules.draw(plugin.challenges(), r.used, active.size(), c, plugin.cfg().kindWeights(), rnd);
            if (draw == null) {
                r.nextDrawAt = System.currentTimeMillis() + RETRY_MS;   // nothing fits right now (e.g. Nether-only pool, all closed)
                return;
            }
            if (draw.poolReset()) r.used.clear();
            choice = draw.choice();
        }
        start(r, choice, chosen, active);
    }

    private void start(Round r, DrawRules.Choice choice, List<UUID> chosen, List<UUID> active) {
        List<UUID> participants = switch (choice.kind()) {
            case SOLO -> List.of(!chosen.isEmpty() ? chosen.get(0) : DrawRules.fewest(counts(r, true), active, rnd));
            case DUEL -> chosen.size() >= 2 ? chosen.subList(0, 2) : DrawRules.fewestTwo(counts(r, false), active, rnd);
            default -> active;
        };
        for (UUID id : participants) {
            Member m = r.member(id);
            if (choice.kind() == Kind.SOLO) m.solos++;
            if (choice.kind() == Kind.DUEL) m.duels++;
        }
        r.used.add(choice.def().id());
        r.challenges++;
        ChallengeRun run = new ChallengeRun(plugin, r, choice.def(), choice.kind(), participants);
        runs.put(r.code, run);
        run.start();
        plugin.rounds().save();
    }

    private static Map<UUID, Integer> counts(Round r, boolean solos) {
        Map<UUID, Integer> out = new HashMap<>();
        for (Member m : r.members.values()) out.put(m.id, solos ? m.solos : m.duels);
        return out;
    }

    private void remindRules(Round r, long now) {
        if (r.rules.isEmpty() || now - r.lastRuleReminder < plugin.cfg().rulesReminderMinutes() * MINUTE_MS) return;
        r.lastRuleReminder = now;
        Msg.round(r, de -> {
            StringJoiner j = new StringJoiner(" <dark_gray>· <white>");
            for (Rule rule : r.rules) j.add(Msg.esc(rule.text(de)));
            return (de ? "Aktive Regeln: <white>" : "Active rules: <white>") + j;
        });
    }

    /** /prost weiter: refused while a challenge runs (Review Focus 3) */
    public boolean next(Round r) {
        if (!DirectorRules.mayDrawNow(runs.containsKey(r.code))) return false;
        r.nextDrawAt = 0;
        return true;
    }

    /** /prost admin force: a given challenge and kind right now (testing, staff) */
    public boolean force(Round r, ChallengeDef d, Kind kind) {
        List<UUID> active = r.activeIds();
        if (runs.containsKey(r.code) || !DrawRules.kinds(d, active.size()).contains(kind)) return false;
        start(r, new DrawRules.Choice(d, kind), List.of(), active);
        return true;
    }

    public ChallengeRun running(Round r) { return runs.get(r.code); }

    public boolean involves(UUID id) {
        for (ChallengeRun run : runs.values()) if (run.involves(id)) return true;
        return false;
    }

    /** a run ended: the next one comes after the reward time (if it was resolved) plus a gap of the round's pace */
    void ended(Round r, boolean resolved) {
        runs.remove(r.code);
        long gap = Pace.gapMs(plugin.cfg().pace().get(r.pace), rnd.nextDouble());
        r.nextDrawAt = DirectorRules.afterRun(System.currentTimeMillis(), resolved ? plugin.cfg().rewardSeconds() : 0, gap);
        plugin.rounds().save();
    }

    /** onDisable: running challenges just stop - nobody drinks, the warm-up starts again after the restart */
    public void shutdown() {
        for (ChallengeRun run : runs.values()) run.stopQuietly();
        runs.clear();
    }
}
