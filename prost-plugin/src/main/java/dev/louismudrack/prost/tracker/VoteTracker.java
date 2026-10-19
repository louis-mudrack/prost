package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.command.ProstCommand;
import dev.louismudrack.prost.config.GameConfig;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.Items;
import dev.louismudrack.prost.ui.Hud;
import dev.louismudrack.prost.ui.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * A build contest in three phases. BUILD: every participant builds for the entry's seconds; /prost here marks the spot
 * (only in a game world and outside the excluded areas), otherwise it is where the builder stands when the time is up.
 * SHOW: the builds in a random order, vote.view-seconds each; a build without a spot is announced as not visitable and
 * stays votable. VOTE: a menu with the other builders' heads for vote.vote-seconds, one vote that may change until the
 * deadline. mode portrait: each builder is told privately whom to portray. Who drops out is no longer votable and their
 * vote is gone. VoteRules judges; every draw comes from ProstPlugin#random, every time from ProstPlugin#now.
 */
public final class VoteTracker extends BaseTracker implements GameTracker {
    private enum Phase { BUILD, SHOW, VOTE }

    private static final long SECOND_MS = 1000L;
    /** when the building ends; a dropped participant never comes back, so fewer while building already decides it */
    private static final int MIN_BUILDERS = 3;
    /** after the building: somebody to vote for */
    private static final int MIN_VOTERS = 2;
    private static final String PORTRAIT = "portrait";

    /** participants in the run's order: the verdict keeps it */
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, Location> spots = new LinkedHashMap<>();
    /** voter -> builder */
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Map<UUID, GameMenu> menus = new HashMap<>();
    /** the builds in the order they are shown */
    private final List<UUID> tour = new ArrayList<>();
    /** portrait: builder -> whom they portray */
    private Map<UUID, UUID> models = Map.of();
    private Phase phase = Phase.BUILD;
    /** the index in tour of the build on show */
    private int shown = -1;
    private boolean startTold;
    private long buildEndsAt, viewEndsAt, voteEndsAt;

    VoteTracker(TrackContext ctx) { super(ctx); }

    @Override
    protected void begin(List<Player> ps) {
        buildEndsAt = ctx.plugin().now() + ctx.def().seconds() * SECOND_MS;
        for (Player p : ps) order.add(p.getUniqueId());
        if (PORTRAIT.equals(ctx.def().mode()) && order.size() >= MIN_VOTERS) models = VoteRules.derangement(order, rnd());
    }

    /** /prost here: the spot is where the builder stands, while building, in a game world and outside the excluded areas */
    public boolean mark(Player p) {
        UUID id = p.getUniqueId();
        if (phase != Phase.BUILD || !order.contains(id)) {
            Msg.error(p, "game.vote.not_now");
            return false;
        }
        Location at = p.getLocation();
        if (!inGameWorld(at)) {
            Msg.error(p, "game.vote.wrong_world");
            return false;
        }
        if (excluded(at)) {
            Msg.error(p, "game.vote.excluded");
            return false;
        }
        spots.put(id, at);
        Msg.tell(p, "game.vote.marked", place(id, at));
        Sounds.success(p);
        return true;
    }

    /** the builds that have a spot, for the tour; a spot whose world was unloaded since is none */
    Map<UUID, Location> spots() {
        Map<UUID, Location> out = new LinkedHashMap<>();
        for (UUID id : spots.keySet()) {
            Location spot = spotOf(id);
            if (spot != null) out.put(id, spot);
        }
        return out;
    }

    @Override
    public long deadline() {
        return switch (phase) {
            case BUILD -> buildEndsAt;
            case SHOW -> viewEndsAt;
            case VOTE -> voteEndsAt;
        };
    }

    /** a vote may change until the deadline */
    @Override
    public boolean decided() { return false; }

    @Override
    public Verdict verdict() { return VoteRules.verdict(order, votes, ctx.def().sips()); }

    @Override
    public String cancelKey() { return order.size() < (phase == Phase.BUILD ? MIN_BUILDERS : MIN_VOTERS) ? "game.vote.too_few" : null; }

    /** a watcher's list never tells whom somebody voted for */
    @Override
    public String status(UUID viewer, UUID who) {
        boolean own = viewer.equals(who);
        return switch (phase) {
            case BUILD -> {
                Location spot = spotOf(who);
                if (!own) yield Msg.str(viewer, spot == null ? "game.vote.building" : "game.vote.has_spot");
                yield spot == null ? Msg.str(viewer, "game.vote.build") : Msg.str(viewer, "game.vote.built", place(who, spot));
            }
            case SHOW -> {
                UUID builder = tour.get(shown);
                if (own) yield Msg.str(viewer, "game.vote.showing", V.of("name", name(builder)));
                yield Msg.str(viewer, who.equals(builder) ? "game.vote.on_show" : "game.vote.looking");
            }
            case VOTE -> {
                UUID choice = votes.get(who);
                if (own) yield choice == null ? Msg.str(viewer, "game.vote.choose") : Msg.str(viewer, "game.vote.voted", V.of("name", name(choice)));
                yield Msg.str(viewer, choice == null ? "game.vote.voting" : "game.vote.has_voted");
            }
        };
    }

    /** "Votes: Anna 2 · Ben 1 · Cleo 0", the most first; null keeps the default line (the vote never started) */
    @Override
    public String result(UUID viewer) {
        if (phase != Phase.VOTE) return null;
        Map<UUID, Integer> counts = VoteRules.counts(order, votes);
        List<UUID> ranked = new ArrayList<>(order);
        ranked.sort(Comparator.comparingInt((UUID id) -> counts.get(id)).reversed());
        StringJoiner j = new StringJoiner(" <dark_gray>· ");
        for (UUID id : ranked) j.add("<white>" + Msg.esc(name(id)) + " <hl>" + counts.get(id));
        return Msg.str(viewer, "game.vote.result", V.mm("votes", j.toString()));
    }

    /** out of the contest: not votable, the own vote gone, a vote for them is no vote any more, their build no longer on show */
    @Override
    public void dropped(UUID id) {
        boolean onShow = phase == Phase.SHOW && id.equals(tour.get(shown));
        order.remove(id);
        spots.remove(id);
        votes.remove(id);
        close(id, menus.remove(id));
        List<UUID> voters = new ArrayList<>();
        votes.forEach((voter, builder) -> { if (builder.equals(id)) voters.add(voter); });
        for (UUID voter : voters) {
            votes.remove(voter);
            Player p = Bukkit.getPlayer(voter);
            if (p != null) Msg.tell(p, "game.vote.vote_gone", V.of("name", name(id)));
        }
        for (GameMenu menu : menus.values()) menu.redraw();
        // too few left: the run cancels on this tick, announcing the next build first would be noise
        if (onShow && cancelKey() == null) showNext();
    }

    @Override
    public boolean reopen(Player p) {
        GameMenu menu = menus.get(p.getUniqueId());
        if (menu == null) return false;
        menu.open();
        return true;
    }

    @Override
    public void poll(List<Player> online) {
        if (!startTold) tellStart();
        long now = ctx.plugin().now();
        if (phase == Phase.BUILD && now >= buildEndsAt) endBuilding();
        else if (phase == Phase.SHOW && now >= viewEndsAt) showNext();
    }

    /** inline: onDisable stops the run through here */
    @Override
    public void stop() {
        super.stop();
        menus.forEach((id, menu) -> close(id, menu));
        menus.clear();
    }

    // ---------------------------------------------------------------------------------------------- phases

    /** on the first poll: the run announces the challenge after starting the tracker, these lines belong after it */
    private void tellStart() {
        startTold = true;
        for (UUID id : order) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Msg.tell(p, "game.vote.here_button", V.of("cmd", ProstCommand.RUN_HERE));
            UUID model = models.get(id);
            if (model != null) Msg.tell(p, "game.vote.model", V.of("name", name(model)));
        }
    }

    /** a builder without a mark builds where they stand; too few left: the run cancels (cancelKey) */
    private void endBuilding() {
        if (order.size() < MIN_BUILDERS) return;
        for (UUID id : order) {
            Player p = Bukkit.getPlayer(id);
            if (spots.containsKey(id) || p == null) continue;
            Location at = p.getLocation();
            if (inGameWorld(at) && !excluded(at)) spots.put(id, at);
        }
        tour.addAll(order);
        Collections.shuffle(tour, rnd());
        phase = Phase.SHOW;
        showNext();
    }

    /** the next build still in the contest; one without a spot is announced and passed; after the last the vote starts */
    private void showNext() {
        while (++shown < tour.size()) {
            UUID builder = tour.get(shown);
            if (!order.contains(builder)) continue;
            Location spot = spotOf(builder);
            present(builder, spot);
            if (spot != null) {
                viewEndsAt = ctx.plugin().now() + games().voteViewSeconds() * SECOND_MS;
                return;
            }
        }
        startVote();
    }

    /** a build's turn: every round member hears whose it is and where it stands; one without a spot is only named in chat */
    private void present(UUID builder, Location spot) {
        for (UUID id : ctx.round().members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (spot == null) {
                Msg.tell(p, "game.vote.show_missing", V.of("name", name(builder)));
                continue;
            }
            String title = "<primary>" + Msg.str(id, "game.vote.show_title", V.of("name", name(builder))) + "</primary>";
            Hud.title(p, title, Msg.str(id, "game.vote.show_place", place(builder, spot)));
            Msg.tell(p, "game.vote.show_at", place(builder, spot));
            Sounds.ding(p);
        }
    }

    private void startVote() {
        phase = Phase.VOTE;
        voteEndsAt = ctx.plugin().now() + games().voteVoteSeconds() * SECOND_MS;
        for (UUID id : order) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            GameMenu menu = new GameMenu(ctx.plugin(), p, "game.vote.title", m -> draw(m, id));
            menu.until(voteEndsAt);
            menus.put(id, menu);
            menu.open();
            Msg.tell(p, "game.vote.vote_now");
            Msg.tell(p, "game.reopen", V.of("cmd", ProstCommand.RUN_PLAY));
        }
    }

    // ---------------------------------------------------------------------------------------------- the vote menu

    /** the other builders' heads; the chosen one says so and cannot be chosen again */
    private void draw(GameMenu m, UUID viewer) {
        List<UUID> others = order.stream().filter(id -> !id.equals(viewer)).limit(MenuSlots.MAX).toList();
        UUID choice = votes.get(viewer);
        for (int i = 0; i < others.size(); i++) {
            UUID builder = others.get(i);
            boolean chosen = builder.equals(choice);
            List<String> lore = List.of(Msg.str(viewer, chosen ? "game.vote.your_vote" : "game.vote.click"));
            m.button(MenuSlots.of(i, others.size()), Items.head(builder, "<hl>" + Msg.esc(name(builder)), lore),
                    chosen ? null : () -> vote(viewer, builder));
        }
    }

    private void vote(UUID voter, UUID builder) {
        if (phase != Phase.VOTE || !order.contains(voter) || !order.contains(builder) || voter.equals(builder)) return;
        votes.put(voter, builder);
        GameMenu menu = menus.get(voter);
        if (menu != null) menu.redraw();
    }

    private static void close(UUID id, GameMenu menu) {
        Player p = Bukkit.getPlayer(id);
        if (menu != null && p != null && menu.isOpen()) p.closeInventory();
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private boolean inGameWorld(Location at) { return ctx.plugin().pluginConfig().gameWorld(at.getWorld().getName()); }

    private boolean excluded(Location at) { return ctx.plugin().hooks().excluded(at); }

    /** the builder's spot; a world unloaded since the mark makes it none (Location#getWorld would throw) */
    private Location spotOf(UUID builder) {
        Location spot = spots.get(builder);
        return spot != null && spot.isWorldLoaded() ? spot : null;
    }

    /** the builder's name and the spot's world and block, each escaped */
    private V[] place(UUID builder, Location at) {
        return new V[]{V.of("name", name(builder)), V.of("world", at.getWorld().getName()), V.of("x", at.getBlockX()),
                V.of("y", at.getBlockY()), V.of("z", at.getBlockZ())};
    }

    private GameConfig.Games games() { return ctx.plugin().cfg().games(); }

    private Random rnd() { return ctx.plugin().random(); }
}
