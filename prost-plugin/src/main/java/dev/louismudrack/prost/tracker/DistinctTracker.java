package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.catalogue.Tags;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** how many DIFFERENT things from a source (mine, collect, consume, craft, kill, biome) - same guards as the source */
final class DistinctTracker extends BaseTracker {
    private final String source;
    private final Set<Material> mats;
    private final Set<EntityType> ents;
    private final Map<UUID, Set<String>> seen = new HashMap<>();

    DistinctTracker(TrackContext ctx) {
        super(ctx);
        source = ctx.def().source();
        boolean kills = "kill".equals(source);
        mats = kills ? java.util.EnumSet.noneOf(Material.class) : materials(ctx.def().targets());
        ents = kills ? entities(ctx.def().targets()) : java.util.EnumSet.noneOf(EntityType.class);
    }

    private void saw(UUID id, String key) {
        Set<String> s = seen.computeIfAbsent(id, k -> new HashSet<>());
        if (s.add(key)) progress.put(id, s.size());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!"mine".equals(source) || !tracks(p) || p.getGameMode() != GameMode.SURVIVAL) return;
        Material type = e.getBlock().getType();
        if (!mats.contains(type) || ctx.plugin().placed().placed(ctx.round(), e.getBlock())) return;
        saw(p.getUniqueId(), Tags.distinctKey(type.name()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (!"collect".equals(source) || !(e.getEntity() instanceof Player p) || !tracks(p) || e.getItem().getThrower() != null) return;
        Material type = e.getItem().getItemStack().getType();
        if (mats.isEmpty() || mats.contains(type)) saw(p.getUniqueId(), type.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!"consume".equals(source) || !tracks(e.getPlayer())) return;
        Material type = e.getItem().getType();
        if (mats.isEmpty() ? type.isEdible() : mats.contains(type)) saw(e.getPlayer().getUniqueId(), type.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!"craft".equals(source) || !(e.getWhoClicked() instanceof Player p) || !tracks(p)) return;
        Material type = e.getRecipe().getResult().getType();
        if (mats.contains(type)) saw(p.getUniqueId(), type.name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (!"kill".equals(source) || p == null || !tracks(p) || !ents.contains(e.getEntityType())) return;
        saw(p.getUniqueId(), e.getEntityType().name());
    }

    @Override
    public void poll(List<Player> online) {
        if (!"biome".equals(source)) return;
        for (Player p : online) if (tracks(p)) saw(p.getUniqueId(), p.getLocation().getBlock().getBiome().getKey().toString());
    }
}
