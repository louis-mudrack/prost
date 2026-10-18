package dev.louismudrack.prost;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** seconds spent wearing the target on the head, summed over the challenge */
final class WearTracker extends BaseTracker {
    private final Set<Material> targets;
    private final Map<UUID, Long> wornMs = new HashMap<>();
    private long lastPoll;

    WearTracker(TrackContext ctx) {
        super(ctx);
        targets = materials(ctx.def().targets());
    }

    @Override
    protected void begin(List<Player> ps) { lastPoll = System.currentTimeMillis(); }

    @Override
    public void poll(List<Player> online) {
        long now = System.currentTimeMillis(), step = now - lastPoll;
        lastPoll = now;
        for (Player p : online) {
            if (!tracks(p)) continue;
            ItemStack head = p.getInventory().getHelmet();
            if (head == null || !targets.contains(head.getType())) continue;
            long total = wornMs.merge(p.getUniqueId(), step, Long::sum);
            progress.put(p.getUniqueId(), (int) (total / 1000));
        }
    }
}
