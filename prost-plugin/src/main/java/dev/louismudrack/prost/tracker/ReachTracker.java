package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.hook.Gate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.structure.GeneratedStructure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * reach a height, a distance from the own start, a dimension (through a portal) or a village. Positions on the sky
 * island and anything right after a /spawn or plugin teleport never count (spec §5).
 */
final class ReachTracker extends BaseTracker {
    private static final long TELEPORT_GRACE_MS = 10_000L;
    private static final Set<PlayerTeleportEvent.TeleportCause> PORTALS = Set.of(PlayerTeleportEvent.TeleportCause.NETHER_PORTAL,
            PlayerTeleportEvent.TeleportCause.END_PORTAL, PlayerTeleportEvent.TeleportCause.END_GATEWAY);
    private static final Set<PlayerTeleportEvent.TeleportCause> ARTIFICIAL = Set.of(PlayerTeleportEvent.TeleportCause.COMMAND,
            PlayerTeleportEvent.TeleportCause.PLUGIN, PlayerTeleportEvent.TeleportCause.UNKNOWN, PlayerTeleportEvent.TeleportCause.SPECTATE);

    private final String mode, value;
    private final Map<UUID, Location> starts = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    ReachTracker(TrackContext ctx) {
        super(ctx);
        mode = ctx.def().mode();
        value = ctx.def().value();
    }

    @Override
    protected void begin(List<Player> ps) { for (Player p : ps) starts.put(p.getUniqueId(), p.getLocation()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (!tracks(p)) return;
        if (ARTIFICIAL.contains(e.getCause())) lastTeleport.put(p.getUniqueId(), System.currentTimeMillis());
        if (!"dimension".equals(mode) || !PORTALS.contains(e.getCause()) || e.getTo().getWorld() == null) return;
        World.Environment env = e.getTo().getWorld().getEnvironment();
        if (("nether".equals(value) && env == World.Environment.NETHER) || ("the_end".equals(value) && env == World.Environment.THE_END))
            reach(p.getUniqueId());
    }

    @Override
    public void poll(List<Player> online) {
        long now = System.currentTimeMillis();
        for (Player p : online) {
            UUID id = p.getUniqueId();
            if (!tracks(p) || progress(id) > 0) continue;
            Location l = p.getLocation();
            if (Gate.inSpawn(l) || now - lastTeleport.getOrDefault(id, 0L) < TELEPORT_GRACE_MS) continue;
            boolean reached = switch (mode) {
                case "below" -> l.getY() <= Double.parseDouble(value);
                case "above" -> l.getY() >= Double.parseDouble(value);
                case "distance" -> {
                    Location s = starts.get(id);
                    yield s != null && s.getWorld() == l.getWorld() && Math.hypot(l.getX() - s.getX(), l.getZ() - s.getZ()) >= Double.parseDouble(value);
                }
                case "structure" -> inVillage(p);
                default -> false;
            };
            if (reached) reach(id);
        }
    }

    private static boolean inVillage(Player p) {
        Location l = p.getLocation();
        for (GeneratedStructure gs : p.getChunk().getStructures())
            if (gs.getStructure().getKey().getKey().startsWith("village") && gs.getBoundingBox().contains(l.toVector())) return true;
        return false;
    }
}
