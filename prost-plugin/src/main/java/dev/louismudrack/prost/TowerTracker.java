package dev.louismudrack.prost;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** the highest block a player placed, counted above where they stood at the start */
final class TowerTracker extends BaseTracker {
    private final Map<UUID, Integer> startY = new HashMap<>();

    TowerTracker(TrackContext ctx) { super(ctx); }

    @Override
    protected void begin(List<Player> ps) { for (Player p : ps) startY.put(p.getUniqueId(), p.getLocation().getBlockY()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (!tracks(p)) return;
        int height = e.getBlock().getY() - startY.getOrDefault(p.getUniqueId(), e.getBlock().getY());
        progress.merge(p.getUniqueId(), height, Math::max);
    }
}
