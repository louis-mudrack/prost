package dev.louismudrack.prost.tracker;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;

/** blocks broken in survival mode; blocks a member placed during the round never count */
final class MineTracker extends BaseTracker {
    private final Set<Material> targets;

    MineTracker(TrackContext ctx) {
        super(ctx);
        targets = materials(ctx.def().targets());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!tracks(p) || p.getGameMode() != GameMode.SURVIVAL || !targets.contains(e.getBlock().getType())) return;
        if (ctx.plugin().placed().placed(ctx.round(), e.getBlock())) return;
        add(p.getUniqueId(), 1);
    }
}
