package dev.louismudrack.prost.tracker;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * all: every participant within `value` blocks of every other at once; block: all on one block (1 block apart, same
 * height +-1); hug: crouch while another participant is within `value` blocks. all/block stay reached once met.
 */
final class ProximityTracker extends BaseTracker {
    private static final long HUG_COOLDOWN_MS = 500L;

    private final String mode;
    private final double radius;
    private final Map<UUID, Long> lastHug = new HashMap<>();

    ProximityTracker(TrackContext ctx) {
        super(ctx);
        mode = ctx.def().mode();
        radius = Double.parseDouble(ctx.def().value());
    }

    @Override
    public void poll(List<Player> online) {
        // online = the run's current participants: whoever dropped out (paused, AFK, gone) no longer has to be there
        if ("hug".equals(mode) || online.size() < 2) return;
        for (Player a : online) for (Player b : online) {
            if (a == b) continue;
            if (a.getWorld() != b.getWorld()) return;
            if ("block".equals(mode)) {
                double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
                if (Math.hypot(dx, dz) > 1.0 || Math.abs(a.getY() - b.getY()) > 1.0) return;
            } else if (a.getLocation().distance(b.getLocation()) > radius) return;
        }
        for (Player p : online) reach(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!"hug".equals(mode) || !e.isSneaking() || !tracks(p)) return;
        long now = System.currentTimeMillis();
        if (now - lastHug.getOrDefault(p.getUniqueId(), 0L) < HUG_COOLDOWN_MS) return;
        for (UUID other : players) {
            Player o = p.getServer().getPlayer(other);
            if (o == null || o == p || o.getWorld() != p.getWorld() || o.getLocation().distance(p.getLocation()) > radius) continue;
            lastHug.put(p.getUniqueId(), now);
            add(p.getUniqueId(), 1);
            return;
        }
    }
}
