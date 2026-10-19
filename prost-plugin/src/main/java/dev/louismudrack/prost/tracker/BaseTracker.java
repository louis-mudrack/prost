package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.catalogue.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared plumbing: participants and their names, a progress map, listener registration. */
abstract class BaseTracker implements Tracker, Listener {
    protected final TrackContext ctx;
    protected final Set<UUID> players = new HashSet<>();
    protected final Map<UUID, Integer> progress = new ConcurrentHashMap<>();
    /** kept from the start: a member who leaves or is kicked is gone from the round before the run drops them */
    private final Map<UUID, String> names = new HashMap<>();

    BaseTracker(TrackContext ctx) { this.ctx = ctx; }

    @Override
    public void start(List<Player> ps) {
        for (Player p : ps) {
            players.add(p.getUniqueId());
            progress.put(p.getUniqueId(), 0);
            names.put(p.getUniqueId(), p.getName());
        }
        begin(ps);
        Bukkit.getPluginManager().registerEvents(this, ctx.plugin());
    }

    /** snapshots taken at the start (stats, positions) */
    protected void begin(List<Player> ps) { }

    @Override
    public int progress(UUID id) { return progress.getOrDefault(id, 0); }

    @Override
    public void stop() { HandlerList.unregisterAll(this); }

    protected boolean tracks(Player p) { return players.contains(p.getUniqueId()); }

    /** a participant's name as it was at the start; "?" for anybody else */
    protected String name(UUID id) { return id == null ? "?" : names.getOrDefault(id, "?"); }

    protected void add(UUID id, int n) { progress.merge(id, n, Integer::sum); }

    protected void reach(UUID id) { progress.put(id, Math.max(1, ctx.def().amount())); }

    static Set<Material> materials(List<String> targets) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        List<String> names = Tags.resolveAll(targets);
        if (names != null) for (String n : names) { Material m = Material.getMaterial(n); if (m != null) out.add(m); }
        return out;
    }

    /** only for entity targets: CatalogueRules has checked every name against EntityType at load */
    static Set<EntityType> entities(List<String> targets) {
        Set<EntityType> out = EnumSet.noneOf(EntityType.class);
        List<String> names = Tags.resolveAll(targets);
        if (names != null) for (String n : names) out.add(EntityType.valueOf(n));
        return out;
    }
}
