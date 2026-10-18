package dev.louismudrack.prost.tracker;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.Set;

/** events without a vanilla statistic: blocks placed, items eaten (empty targets = any food) */
final class CountTracker extends BaseTracker {
    private final Set<Material> targets;
    private final boolean place;

    CountTracker(TrackContext ctx) {
        super(ctx);
        targets = materials(ctx.def().targets());
        place = "place".equals(ctx.def().mode());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (place && tracks(e.getPlayer())) add(e.getPlayer().getUniqueId(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (place || !tracks(e.getPlayer())) return;
        Material type = e.getItem().getType();
        if (targets.isEmpty() ? type.isEdible() : targets.contains(type)) add(e.getPlayer().getUniqueId(), 1);
    }
}
