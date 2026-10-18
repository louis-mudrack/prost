package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Blocks placed by a round's members during that round: "mine X" never counts them (no place-and-mine loops, spec §5). */
public final class PlacedBlocks implements Listener {
    private record Pos(UUID world, int x, int y, int z) {}

    private final ProstPlugin plugin;
    private final Map<String, Set<Pos>> byRound = new HashMap<>();

    public PlacedBlocks(ProstPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Round r = plugin.rounds().of(e.getPlayer().getUniqueId());
        if (r != null) byRound.computeIfAbsent(r.code, k -> new HashSet<>()).add(pos(e.getBlock()));
    }

    public boolean placed(Round r, Block b) {
        Set<Pos> s = byRound.get(r.code);
        return s != null && s.contains(pos(b));
    }

    void forget(Round r) { byRound.remove(r.code); }

    private static Pos pos(Block b) { return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }
}
