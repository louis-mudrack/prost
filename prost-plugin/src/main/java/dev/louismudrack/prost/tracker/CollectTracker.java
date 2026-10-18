package dev.louismudrack.prost.tracker;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.Set;
import java.util.UUID;

/**
 * collect: picked-up items that nobody threw (drop-and-pick-up loops never count, spec §5).
 * gift: picked-up items thrown by ANOTHER member of the round - counts once per gift.
 */
final class CollectTracker extends BaseTracker {
    private final Set<Material> targets;
    private final boolean gift;

    CollectTracker(TrackContext ctx, boolean gift) {
        super(ctx);
        this.targets = materials(ctx.def().targets());
        this.gift = gift;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player p) || !tracks(p)) return;
        Material type = e.getItem().getItemStack().getType();
        if (!targets.isEmpty() && !targets.contains(type)) return;
        UUID thrower = e.getItem().getThrower();
        if (gift) {
            if (thrower != null && !thrower.equals(p.getUniqueId()) && ctx.round().member(thrower) != null) add(p.getUniqueId(), 1);
            return;
        }
        if (thrower == null) add(p.getUniqueId(), e.getItem().getItemStack().getAmount() - e.getRemaining());
    }
}
