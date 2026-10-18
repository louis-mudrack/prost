package dev.louismudrack.prost.tracker;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** a goal plus also-avoid bans (e.g. kill a creeper without explosion damage): progress from the goal, broken from the bans */
final class CompositeTracker implements Tracker {
    private final Tracker goal, bans;

    CompositeTracker(Tracker goal, Tracker bans) {
        this.goal = goal;
        this.bans = bans;
    }

    @Override public void start(List<Player> players) { goal.start(players); bans.start(players); }
    @Override public int progress(UUID id) { return goal.progress(id); }
    @Override public boolean broken(UUID id) { return bans.broken(id); }
    @Override public void poll(List<Player> online) { goal.poll(online); bans.poll(online); }
    @Override public void stop() { goal.stop(); bans.stop(); }
}
