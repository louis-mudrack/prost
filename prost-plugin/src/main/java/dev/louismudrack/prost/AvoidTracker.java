package dev.louismudrack.prost;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Not doing something until the time is up (spec §5). Chat arrives on the async chat thread: the only thing it
 * touches is the concurrent "broken" set, which the run reads on the main thread (Review Focus 2).
 */
final class AvoidTracker extends BaseTracker {
    private final Set<String> modes;
    private final Set<UUID> broken = ConcurrentHashMap.newKeySet();

    AvoidTracker(TrackContext ctx, Set<String> modes) {
        super(ctx);
        this.modes = modes;
    }

    @Override
    public int progress(UUID id) { return broken.contains(id) ? 0 : 1; }

    @Override
    public boolean broken(UUID id) { return broken.contains(id); }

    private void hit(Player p, String mode) { if (modes.contains(mode) && tracks(p)) broken.add(p.getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFinalDamage() <= 0) return;
        hit(p, "damage");
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION || c == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) hit(p, "explosion-damage");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) { hit(e.getPlayer(), "death"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) { hit(e.getPlayer(), "jump"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent e) { if (e.isSprinting()) hit(e.getPlayer(), "sprint"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) { hit(e.getPlayer(), "eat"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) { hit(e.getPlayer(), "chat"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { hit(e.getPlayer(), "break"); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && !(e.getEntity() instanceof Player)) hit(killer, "kill");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent e) {
        if (e.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) hit(e.getPlayer(), "sleep");
    }

    @Override
    public void poll(List<Player> online) { for (Player p : online) if (p.isInWater()) hit(p, "water"); }
}
