package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.config.YamlErrors;
import dev.louismudrack.prost.i18n.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The way back from a build contest tour: where each touring player stood when it started, written to returns.yml before
 * anyone moves, so a quit, a stop or a crash still gets them back - inline on a stop, on the next join or respawn
 * otherwise. From the start of the tour until it is over for them, and RETURN_GRACE_MS after a return, they take no
 * damage and cannot change or take anything at a build: no blocks, buckets or block and item use, no using, hitting or
 * breaking entities, no dropping or picking up items.
 */
public final class Returns implements Listener {
    private static final String FILE = "returns.yml";
    /** spec §7: the arrival may need a moment (chunks loading) before the player is on their own again */
    private static final long RETURN_GRACE_MS = 3000L;
    /** the shelter of a player on the tour: it lasts until the tour is over for them */
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

    /** what a crash left behind; an empty file is nobody to bring back, an unreadable one is set aside */
    public void load() {
        Object parsed;
        try {
            parsed = plugin.files().read(FILE);
        } catch (IOException | RuntimeException e) {   // SnakeYAML's YAMLException is a RuntimeException
            setAside(YamlErrors.describe(e));
            return;
        }
        if (parsed == null) return;
        if (!(parsed instanceof Map<?, ?> saved)) {
            setAside("the top level is not a map of players");
            return;
        }
        saved.forEach((key, value) -> {
            Home home = Home.decode(value);
            UUID id = uuid(String.valueOf(key));
            if (id == null || home == null) plugin.getLogger().warning(FILE + ": the entry " + key + " is incomplete - skipped");
            else homes.put(id, home);
        });
    }

    /** the next save would overwrite it: moved next to it under a name no save uses, for the admin to repair */
    private void setAside(String problem) {
        Path file = new File(plugin.getDataFolder(), FILE).toPath(), aside = file.resolveSibling(FILE + ".broken");
        for (int n = 2; Files.exists(aside); n++) aside = file.resolveSibling(FILE + ".broken." + n);
        try {
            Files.move(file, aside);
            plugin.getLogger().warning(FILE + " is unreadable (" + problem + ") - moved to " + aside.getFileName()
                    + ", nobody is brought back from it; to use it, stop the server, fix it, rename it to " + FILE + " and start again");
        } catch (IOException e) {
            plugin.getLogger().warning(FILE + " is unreadable (" + problem + ") and could not be moved aside (" + YamlErrors.describe(e)
                    + ") - copy it somewhere else now, the next build contest tour replaces it");
        }
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Before anyone moves: each player's way back, on disk at once; they are sheltered until the tour is over for them.
     * False (one warning) when returns.yml cannot be written - then nothing is kept and nobody may be moved.
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
     * Back to where they stood when the tour started, out of any vehicle, no longer burning or freezing; true if it moved
     * them. The tour is over for them either way: the shelter ends, with the grace only after a return. Dead: the respawn
     * brings them back. A return that cannot be made yet is told to them and tried again when they join.
     */
    public boolean bringBack(Player p) {
        int before = homes.size();
        boolean moved = moveBack(p);
        saveIfFewer(before);
        return moved;
    }

    /** the end of a tour: each of them back as bringBack does, with one save for all; one failed return keeps nobody else away */
    public void bringBackAll(Collection<Player> ps) {
        int before = homes.size();
        for (Player p : ps) {
            try {
                moveBack(p);
            } catch (RuntimeException e) {
                String what = p.getName() + " could not be brought back from the tour - tried again when they join";
                plugin.getLogger().log(Level.WARNING, what, e);
            }
        }
        saveIfFewer(before);
    }

    /** bringBack without saving: the caller saves once for everybody it brings back */
    private boolean moveBack(Player p) {
        UUID id = p.getUniqueId();
        endShelter(id);
        Home home = homes.get(id);
        if (home == null || p.isDead()) return false;
        extinguish(p);
        World world = worldOf(p, home);
        if (world == null) return false;
        p.leaveVehicle();
        if (!p.teleport(home.in(world))) {
            plugin.getLogger().warning(p.getName() + " could not be moved back to " + home + " - tried again when they join");
            Msg.tell(p, "game.vote.return_later");
            return false;
        }
        returned(p);
        return true;
    }

    /** the way back is gone, and with it the shelter that lasts until they are back; saveIfFewer writes it down */
    private void forget(UUID id) {
        endShelter(id);
        homes.remove(id);
    }

    /** the ways back dropped since {@code before} (homes.size() then) leave returns.yml */
    private void saveIfFewer(int before) {
        if (homes.size() < before) save(" - a player who is back may be moved there again on joining after a restart");
    }

    private void returned(Player p) {
        forget(p.getUniqueId());
        sheltered.put(p.getUniqueId(), plugin.now() + RETURN_GRACE_MS);
        Msg.tell(p, "game.vote.returned");
    }

    /** a running grace stays */
    private void endShelter(UUID id) { sheltered.remove(id, UNTIL_BACK); }

    /** fire or frost picked up at a build while sheltered must not hurt them at home */
    private static void extinguish(Player p) {
        p.setFireTicks(0);
        p.setFreezeTicks(0);
    }

    /** loaded: the world; unloaded: null, the way back waits for the next join; no folder either: gone, dropped */
    private World worldOf(Player p, Home home) {
        World world = Bukkit.getWorld(home.world());
        if (world != null) return world;
        if (new File(Bukkit.getWorldContainer(), home.world()).isDirectory()) {
            plugin.getLogger().warning(p.getName() + "'s way back leads to " + home + ", but the world " + home.world()
                    + " is not loaded - tried again when they join");
            Msg.tell(p, "game.vote.return_later");
        } else {
            forget(p.getUniqueId());
            plugin.getLogger().warning(p.getName() + "'s way back leads to " + home + ", but the world " + home.world()
                    + " no longer exists - dropped");
            Msg.tell(p, "game.vote.return_gone");
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

    /** a hit or a removal by a sheltered player: by themselves, or by a projectile they shot */
    private boolean sheltersCause(Entity cause) {
        if (cause instanceof Projectile shot && shot.getShooter() instanceof Player shooter) return shelters(shooter.getUniqueId());
        return cause instanceof Player p && shelters(p.getUniqueId());
    }

    // ---------------------------------------------------------------------------------------------- the way back

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { bringBack(e.getPlayer()); }

    /** died on the tour: they respawn where they stood */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        int before = homes.size();
        Home home = homes.get(p.getUniqueId());
        World world = home == null ? null : worldOf(p, home);
        if (world != null) {
            extinguish(p);
            e.setRespawnLocation(home.in(world));
            returned(p);
        }
        saveIfFewer(before);
    }

    // ---------------------------------------------------------------------------------------------- the shelter

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && shelters(p.getUniqueId())) e.setCancelled(true); }

    /** animals, armor stands, item frames - also with an arrow */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) { if (sheltersCause(e.getDamager())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) { if (sheltersCause(e.getRemover())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) { if (sheltersCause(e.getAttacker())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    /** doors, buttons, containers, flint and steel, pearls; not ignoreCancelled: a click into the air arrives as cancelled */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        if (!shelters(e.getPlayer().getUniqueId())) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    /** its own handler list: the click armor stands get */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUseEntityAt(PlayerInteractAtEntityEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    /** their own item would stay at the stranger's build */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { if (shelters(e.getPlayer().getUniqueId())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && shelters(p.getUniqueId())) e.setCancelled(true); }

    // ---------------------------------------------------------------------------------------------- the file

    /** false (one warning ending in {@code consequence}) when it could not be written */
    private boolean save(String consequence) {
        Map<String, Object> out = new LinkedHashMap<>();
        homes.forEach((id, home) -> out.put(id.toString(), home.encode()));
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try {
            // forced to disk: after a power loss the file still has to bring everyone back
            plugin.files().writeDurably(FILE, (HEADER + new Yaml(o).dump(out)).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning(FILE + " not saved (" + YamlErrors.describe(e) + ")" + consequence);
            return false;
        }
    }
}
