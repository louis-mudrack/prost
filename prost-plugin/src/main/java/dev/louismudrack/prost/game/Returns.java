package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.config.YamlErrors;
import dev.louismudrack.prost.i18n.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The way back from a build contest tour: where each touring player stood when it started, written to returns.yml before
 * anyone moves, so a quit, a stop or a crash still gets them back - inline on a stop, on the next join or respawn
 * otherwise. From the start of the tour until they are back, and RETURN_GRACE_MS after that, they take no damage and
 * cannot break or place blocks or empty buckets.
 */
public final class Returns implements Listener {
    private static final String FILE = "returns.yml";
    /** spec §7: the arrival may need a moment (chunks loading) before the player is on their own again */
    private static final long RETURN_GRACE_MS = 3000L;
    private static final long UNTIL_BACK = Long.MAX_VALUE;
    private static final String HEADER = "# Prost: where each player of a build contest tour goes back to - written by the plugin\n";

    private final ProstPlugin plugin;
    private final Map<UUID, Home> homes = new LinkedHashMap<>();
    /** id -> until when (plugin time) they are sheltered */
    private final Map<UUID, Long> sheltered = new HashMap<>();

    /** a place by its world's name: the world may be unloaded when the place is read */
    record Home(String world, double x, double y, double z, float yaw, float pitch) {
        static Home of(Location at) { return new Home(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch()); }

        Location in(World w) { return new Location(w, x, y, z, yaw, pitch); }

        Map<String, Object> encode() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("world", world);
            out.put("x", x);
            out.put("y", y);
            out.put("z", z);
            out.put("yaw", (double) yaw);
            out.put("pitch", (double) pitch);
            return out;
        }

        /** null when anything is missing */
        static Home decode(Object o) {
            if (!(o instanceof Map<?, ?> m) || !(m.get("world") instanceof String w) || !(m.get("x") instanceof Number x)
                    || !(m.get("y") instanceof Number y) || !(m.get("z") instanceof Number z) || !(m.get("yaw") instanceof Number yaw)
                    || !(m.get("pitch") instanceof Number pitch)) return null;
            return new Home(w, x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw.floatValue(), pitch.floatValue());
        }

        @Override
        public String toString() { return world + " " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z); }
    }

    public Returns(ProstPlugin plugin) { this.plugin = plugin; }

    /** what a crash left behind */
    public void load() {
        if (!(plugin.files().loadIfPresent(FILE) instanceof Map<?, ?> saved)) return;
        saved.forEach((key, value) -> {
            Home home = Home.decode(value);
            UUID id = uuid(String.valueOf(key));
            if (id == null || home == null) plugin.getLogger().warning(FILE + ": the entry " + key + " is incomplete - skipped");
            else homes.put(id, home);
        });
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Before anyone moves: each player's way back, on disk at once; they are sheltered until they are back. False (one
     * warning) when returns.yml cannot be written - then nothing is kept and nobody may be moved.
     */
    public boolean remember(Map<UUID, Location> where) {
        Map<UUID, Home> before = new LinkedHashMap<>(homes);
        where.forEach((id, at) -> homes.put(id, Home.of(at)));
        if (!save(" - the tour only names the builds, nobody is moved")) {
            homes.clear();
            homes.putAll(before);
            return false;
        }
        for (UUID id : where.keySet()) sheltered.put(id, UNTIL_BACK);
        return true;
    }

    /** whether Prost holds a way back for this player */
    public boolean remembers(UUID id) { return homes.containsKey(id); }

    /**
     * Back to where they stood when the tour started, out of any vehicle; true if it moved them. Sheltered for
     * RETURN_GRACE_MS from now either way: the tour is over for them. Dead: the respawn brings them back.
     */
    public boolean bringBack(Player p) {
        UUID id = p.getUniqueId();
        Home home = homes.get(id);
        if (home == null) return false;
        sheltered.put(id, plugin.now() + RETURN_GRACE_MS);
        if (p.isDead()) return false;
        World world = worldOf(p, home);
        if (world == null) return false;
        p.leaveVehicle();
        if (!p.teleport(home.in(world))) {
            plugin.getLogger().warning(p.getName() + " could not be moved back to " + home + " - tried again when they join");
            return false;
        }
        returned(p);
        return true;
    }

    public void forget(UUID id) {
        if (homes.remove(id) != null) save(" - a player who is back may be moved there again on joining after a restart");
    }

    private void returned(Player p) {
        forget(p.getUniqueId());
        sheltered.put(p.getUniqueId(), plugin.now() + RETURN_GRACE_MS);
        Msg.tell(p, "game.vote.returned");
    }

    /** loaded: the world; unloaded: null, the way back waits for the next join; no folder either: gone, dropped */
    private World worldOf(Player p, Home home) {
        World world = Bukkit.getWorld(home.world());
        if (world != null) return world;
        if (new File(Bukkit.getWorldContainer(), home.world()).isDirectory()) {
            plugin.getLogger().warning(p.getName() + "'s way back leads to " + home + ", but the world " + home.world()
                    + " is not loaded - tried again when they join");
        } else {
            forget(p.getUniqueId());
            plugin.getLogger().warning(p.getName() + "'s way back leads to " + home + ", but the world " + home.world()
                    + " no longer exists - dropped");
        }
        return null;
    }

    private boolean shelters(UUID id) {
        Long until = sheltered.get(id);
        if (until == null) return false;
        if (plugin.now() < until) return true;
        sheltered.remove(id);
        return false;
    }

    // ---------------------------------------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { bringBack(e.getPlayer()); }

    /** died on the tour: they respawn where they stood */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Home home = homes.get(p.getUniqueId());
        World world = home == null ? null : worldOf(p, home);
        if (world == null) return;
        e.setRespawnLocation(home.in(world));
        returned(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && shelters(p.getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    // ---------------------------------------------------------------------------------------------- the file

    /**
     * Synchronously, through a temp file: it has to work while the plugin is disabling, and a stop halfway through
     * writing must not leave a broken file. False (one warning ending in {@code consequence}) when it failed.
     */
    private boolean save(String consequence) {
        Map<String, Object> out = new LinkedHashMap<>();
        homes.forEach((id, home) -> out.put(id.toString(), home.encode()));
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Path file = new File(plugin.getDataFolder(), FILE).toPath(), tmp = file.resolveSibling(FILE + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, HEADER + new Yaml(o).dump(out), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {   // some file systems cannot: a plain replace is still better than none
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning(FILE + " not saved (" + YamlErrors.describe(e) + ")" + consequence + removed(tmp));
            return false;
        }
    }

    /** "" once the temp file is gone, else what is left behind */
    private static String removed(Path tmp) {
        try {
            Files.deleteIfExists(tmp);
            return "";
        } catch (IOException e) {
            return "; " + tmp.getFileName() + " is left behind (" + YamlErrors.describe(e) + ")";
        }
    }
}
