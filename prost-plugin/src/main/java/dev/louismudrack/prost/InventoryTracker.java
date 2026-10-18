package dev.louismudrack.prost;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/** holds X of the target in the inventory - decided by the last poll when the time runs out */
final class InventoryTracker extends BaseTracker {
    private final Set<Material> targets;

    InventoryTracker(TrackContext ctx) {
        super(ctx);
        targets = materials(ctx.def().targets());
    }

    @Override
    public void poll(List<Player> online) {
        for (Player p : online) {
            if (!tracks(p)) continue;
            int count = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && targets.contains(it.getType())) count += it.getAmount();
            progress.put(p.getUniqueId(), count);
        }
    }
}
