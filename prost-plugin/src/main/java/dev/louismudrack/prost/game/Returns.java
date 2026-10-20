package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.config.YamlErrors;
import dev.louismudrack.prost.i18n.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
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
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The way back from a build contest tour: where each touring player stood when it started, written to returns.yml before
 * anyone moves, so a quit, a stop or a crash still gets them back - inline on a stop, on the next join or respawn
 * otherwise. From the start of the tour until it is over for them, and RETURN_GRACE_MS after a return, they take no
 * damage and cannot change or take anything at a build: no blocks, buckets or block and item use, no using, hitting or
 * breaking entities, no dropping or picking up items. A return into mid-air keeps the shelter until they land, at most
 * 30 s while they are online (a flyer whose flight holds through the grace is on their own); returns.yml keeps the
 * landing too, so one that a quit, a stop or a reload cut short starts again on their arrival. A return another plugin
 * refuses, or into a world that is not loaded, keeps it while tick tries again: for one minute per way back, from its
 * first failure on and also while they are away (a join within it shelters them again). After that minute each join
 * tries once, without the shelter. A return that is made is finished, also when telling them about it fails.
 */
public final class Returns implements Listener {
    private static final String FILE = "returns.yml";
    /** the top-level key of the landing players' ids: no player id looks like it */
    private static final String LANDING = "landing";
    /** spec §7: the arrival may need a moment (chunks loading) before the player is on their own again */
    private static final long RETURN_GRACE_MS = 3000L;
    /** a return into mid-air: sheltered until they land, at most this long online */
    private static final long LANDING_MAX_MS = 30_000L;
    /** a return that failed is tried again this often while they are online ... */
    private static final long RETRY_MS = 5_000L;
    /** ... for this long after the way back's first failure, online or not; after that each join tries once */
    private static final long RETRY_WINDOW_MS = 60_000L;
    /** the shelter of a player on the tour: it lasts until the tour is over for them */
    private static final long UNTIL_BACK = Long.MAX_VALUE;
    private static final String HEADER = "# Prost: where each player of a build contest tour goes back to - written by the plugin\n";

    private final ProstPlugin plugin;
    private final Map<UUID, Home> homes = new LinkedHashMap<>();
    /** id -> until when (plugin time) they are sheltered */
    private final Map<UUID, Long> sheltered = new HashMap<>();
    /**
     * returned into mid-air: id -> when (plugin time) the landing (re)started, at the return or the arrival that resumed
     * it. Never someone Prost holds a way back for: that return decides about the landing.
     */
    private final Map<UUID, Long> landing = new HashMap<>();
    /** id -> the return that failed, tried again by tick */
    private final Map<UUID, Retry> retries = new HashMap<>();
    /** ways back whose minute of tries is over: apart from retries, so tick never looks at them again */
    private final Set<UUID> triesOver = new HashSet<>();

    /** plugin times: the next try, and when the tries are over; whether an error of this return has been logged */
    private record Retry(long nextAt, long endsAt, boolean errorLogged) {}

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
            if (LANDING.equals(key)) return;
            Home home = Home.decode(value);
            UUID id = uuid(String.valueOf(key));
            if (id == null || home == null) plugin.getLogger().warning(FILE + ": the entry " + key + " is incomplete - skipped");
            else homes.put(id, home);
        });
        if (saved.containsKey(LANDING)) loadLanding(saved.get(LANDING));
    }

    /** after the ways back: each landing waits for its player's arrival, which restarts it; what is unreadable is skipped */
    private void loadLanding(Object value) {
        if (!(value instanceof List<?> ids)) {
            plugin.getLogger().warning(FILE + ": " + LANDING + " is not a list of player ids - skipped");
            return;
        }
        for (Object entry : ids) {
            UUID id = uuid(String.valueOf(entry));
            if (id == null) plugin.getLogger().warning(FILE + ": " + LANDING + " lists " + entry + ", which is not a player id - skipped");
            else if (!homes.containsKey(id)) landing.put(id, plugin.now());
        }
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
        Map<UUID, Long> landingBefore = new HashMap<>(landing);
        where.forEach((id, at) -> homes.put(id, Home.of(at)));
        landing.keySet().removeAll(where.keySet());
        if (!save(" - the tour only names the builds, nobody is moved")) {
            homes.clear();
            homes.putAll(before);
            landing.putAll(landingBefore);
            return false;
        }
        for (UUID id : where.keySet()) {
            sheltered.put(id, UNTIL_BACK);
            retries.remove(id);
            triesOver.remove(id);
        }
        return true;
    }

    /** every half second: who returned into mid-air and has landed gets the grace, a failed return is tried again */
    public void tick() {
        if (landing.isEmpty() && retries.isEmpty()) return;
        if (endLandings()) save(" - a player who has landed may be sheltered again for a moment on joining after a restart");
        retryReturns();
    }

    /** online only: away, a landing waits for their arrival, which restarts it. True if any landing ended */
    private boolean endLandings() {
        boolean ended = false;
        for (Iterator<Map.Entry<UUID, Long>> it = landing.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> started = it.next();
            Player p = Bukkit.getPlayer(started.getKey());
            if (p != null && endLanding(p, plugin.now() - started.getValue())) {
                it.remove();
                ended = true;
            }
        }
        return ended;
    }

    /**
     * true if the landing that has run for {@code since} is over: past the cap its shelter runs out by time, standing on
     * something the grace starts, a flight that held through the grace ends it now. A flight that ended meanwhile is held
     * like a fall.
     */
    private boolean endLanding(Player p, long since) {
        UUID id = p.getUniqueId();
        if (since >= LANDING_MAX_MS) return true;
        if (standsOnSomething(p)) {
            sheltered.put(id, plugin.now() + RETURN_GRACE_MS);
            return true;
        }
        if (p.isFlying() && since >= RETURN_GRACE_MS) {
            sheltered.remove(id);
            return true;
        }
        return false;
    }

    /**
     * online and alive, every RETRY_MS until the minute is over; offline the join, dead the respawn brings them back, while
     * the minute runs on. A rider is left on their mount (the try waits until they are off); one player's error does not
     * stop the others.
     */
    private void retryReturns() {
        long now = plugin.now();
        Map.copyOf(retries).forEach((id, retry) -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                if (now >= retry.endsAt()) endMinute(id);   // nobody to tell: their join tells them
                return;
            }
            if (p.isDead()) return;
            try {
                if (now >= retry.endsAt()) {
                    giveUp(p);
                } else if (now >= retry.nextAt() && !p.isInsideVehicle()) {
                    retries.put(id, new Retry(now + RETRY_MS, retry.endsAt(), retry.errorLogged()));
                    bringBack(p);
                }
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, p.getName() + "'s return could not be tried again", e);
            }
        });
    }

    /** whether Prost holds a way back for this player */
    public boolean remembers(UUID id) { return homes.containsKey(id); }

    /**
     * A player who is (back) on the server: brought back when Prost holds a way back for them, otherwise a landing that a
     * quit, a stop or a reload cut short starts again, sheltered for at most LANDING_MAX_MS from now.
     */
    public void arrive(Player p) {
        UUID id = p.getUniqueId();
        if (remembers(id)) {
            bringBack(p);
        } else if (landing.containsKey(id)) {
            long now = plugin.now();
            landing.put(id, now);
            sheltered.put(id, now + LANDING_MAX_MS);
        }
    }

    /**
     * Back to where they stood when the tour started, out of any vehicle and rid of any passenger, no longer burning or
     * freezing; true if it moved them. The shelter ends, with the grace only after a return. Dead: the respawn brings them
     * back. A return that cannot be made yet is told to them and keeps the shelter while tick tries again, until a minute
     * after the way back's first failure; after that each join tries once, without the shelter. False also for a return
     * that was made but could not be told: it stays made.
     */
    public boolean bringBack(Player p) {
        int before = homes.size();
        boolean moved = moveBackOrRetry(p);
        saveIfFewer(before);
        return moved;
    }

    /** the end of a tour: each of them back as bringBack does, with one save for all; one failed return keeps nobody else away */
    public void bringBackAll(Collection<Player> ps) {
        int before = homes.size();
        for (Player p : ps) {
            try {
                moveBackOrRetry(p);
            } catch (RuntimeException e) {   // from the error path itself: at a stop telling them throws again
                warn(p.getName() + " could not be brought back from the tour - their way back is kept", e);
            }
        }
        saveIfFewer(before);
    }

    /**
     * an error in a return is a failed return like a refused one: told, and the shelter bounded by the minute of tries.
     * Once the way back is forgotten the return is finished (or dropped): an error then came from telling them.
     */
    private boolean moveBackOrRetry(Player p) {
        try {
            return moveBack(p);
        } catch (RuntimeException e) {
            if (remembers(p.getUniqueId())) retry(p, p.getName() + " could not be brought back from the tour", e);
            else warn(p.getName() + " is back from the tour, but telling them failed", e);
            return false;
        }
    }

    /** bringBack without saving: the caller saves once for everybody it brings back */
    private boolean moveBack(Player p) {
        UUID id = p.getUniqueId();
        Home home = homes.get(id);
        if (home == null || p.isDead()) {
            endShelter(id);
            return false;
        }
        extinguish(p);
        World world = Bukkit.getWorld(home.world());
        if (world == null) {
            if (!dropIfGone(p, home)) retry(p, notLoaded(p, home));
            return false;
        }
        p.leaveVehicle();
        p.eject();
        if (!p.teleport(home.in(world))) {
            retry(p, p.getName() + " could not be moved back to " + home);
            return false;
        }
        returned(p, standsOnSomething(p));
        return true;
    }

    private void retry(Player p, String failure) { retry(p, failure, null); }

    /**
     * a return another plugin refused, into a world that is not loaded or that threw {@code error}. The way back's first
     * failure is logged and told and starts its minute of tries: the shelter holds while tick tries again. A failure within
     * the minute is told only while the shelter is not held (a join: their quit ended it), so not on tick's tries, nor a
     * second time when telling it threw; of these only a first error is logged. After the minute (also one that ran out
     * while they were away, before tick saw it), each join tries once, unsheltered. A return refused while the server stops
     * (no tick runs any more) waits for the next join; the minute is kept in memory only, so after a restart the first join
     * whose return fails starts a new one.
     */
    private void retry(Player p, String failure, RuntimeException error) {
        UUID id = p.getUniqueId();
        Retry pending = retries.get(id);
        if (!plugin.isEnabled()) {
            leaveToTheJoin(p, failure, error);
        } else if (minuteOver(id, pending)) {
            endMinute(id);
            leaveToTheJoin(p, failure, error);
        } else if (pending == null) {
            long now = plugin.now();
            sheltered.put(id, UNTIL_BACK);
            retries.put(id, new Retry(now + RETRY_MS, now + RETRY_WINDOW_MS, error != null));
            warn(failure + " - tried again for a minute, then when they join", error);
            Msg.tell(p, "game.vote.return_retry");
        } else {
            if (error != null && !pending.errorLogged()) {
                retries.put(id, new Retry(pending.nextAt(), pending.endsAt(), true));
                warn(failure + " - tried again until the minute is over, then when they join", error);
            }
            if (!shelteredUntilBack(id)) {
                sheltered.put(id, UNTIL_BACK);
                Msg.tell(p, "game.vote.return_retry");
            }
        }
    }

    /** also a minute that ran out while they were away, before tick saw it */
    private boolean minuteOver(UUID id, Retry pending) {
        return triesOver.contains(id) || (pending != null && plugin.now() >= pending.endsAt());
    }

    private void leaveToTheJoin(Player p, String failure, RuntimeException error) {
        warn(failure + " - tried again when they join", error);
        Msg.tell(p, "game.vote.return_later");
    }

    /** with the stack trace when there is an error */
    private void warn(String what, RuntimeException error) { plugin.getLogger().log(Level.WARNING, what, error); }

    /** the minute is over: so is the shelter, the way back waits in returns.yml for the next join */
    private void giveUp(Player p) {
        endMinute(p.getUniqueId());
        Msg.tell(p, "game.vote.return_later");
    }

    /** a running minute ends, and the shelter its tries kept with it: from now on each join tries once */
    private void endMinute(UUID id) {
        if (retries.remove(id) == null) return;
        triesOver.add(id);
        endShelter(id);
    }

    /** the way back is gone, and with it the shelter that lasts until they are back and any tries; saveIfFewer writes it down */
    private void forget(UUID id) {
        endShelter(id);
        homes.remove(id);
        retries.remove(id);
        triesOver.remove(id);
    }

    /** the ways back dropped since {@code before} (homes.size() then) leave returns.yml */
    private void saveIfFewer(int before) {
        if (homes.size() < before) save(" - a player who is back may be moved there again on joining after a restart");
    }

    /**
     * a fall before the return does not count; {@code standing}: the grace starts now, otherwise the landing, which tick
     * ends. The caller's save of the ways back writes the landing down too.
     */
    private void returned(Player p, boolean standing) {
        UUID id = p.getUniqueId();
        forget(id);
        p.setFallDistance(0f);
        if (standing) {
            sheltered.put(id, plugin.now() + RETURN_GRACE_MS);
        } else {
            sheltered.put(id, plugin.now() + LANDING_MAX_MS);
            landing.put(id, plugin.now());
        }
        Msg.tell(p, "game.vote.returned");
    }

    /**
     * read live from where they are, not from the client's isOnGround or isInWater (both stale right after a teleport):
     * climbing, riding, water at the feet, or a solid block just below them. Not flying: a flight can end right after the
     * return, tick watches it. Anything unclear (lava, a fence top, seagrass, an unloaded chunk) is no: the shelter holds.
     */
    private static boolean standsOnSomething(Player p) {
        if (p.isClimbing() || p.isInsideVehicle()) return true;
        Location feet = p.getLocation();
        if (!feet.isChunkLoaded()) return false;
        return isWater(feet.getBlock()) || feet.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid();
    }

    private static boolean isWater(Block b) {
        return b.getType() == Material.WATER || b.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
    }

    /** a running grace stays; while a minute of tries runs only a quit may end this shelter - retry tells a join from tick's tries by it */
    private void endShelter(UUID id) { sheltered.remove(id, UNTIL_BACK); }

    /** the shelter of the tour, or the one the tries keep */
    private boolean shelteredUntilBack(UUID id) { return sheltered.getOrDefault(id, 0L) == UNTIL_BACK; }

    /** fire or frost picked up at a build while sheltered must not hurt them at home */
    private static void extinguish(Player p) {
        p.setFireTicks(0);
        p.setFreezeTicks(0);
    }

    /** for a world that is not loaded: true when its folder is gone too - then so is the way back (logged, told) */
    private boolean dropIfGone(Player p, Home home) {
        if (new File(Bukkit.getWorldContainer(), home.world()).isDirectory()) return false;
        forget(p.getUniqueId());
        plugin.getLogger().warning(p.getName() + "'s way back leads to " + home + ", but the world " + home.world()
                + " no longer exists - dropped");
        Msg.tell(p, "game.vote.return_gone");
        return true;
    }

    private static String notLoaded(Player p, Home home) {
        return p.getName() + "'s way back leads to " + home + ", but the world " + home.world() + " is not loaded";
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
    public void onJoin(PlayerJoinEvent e) { arrive(e.getPlayer()); }

    /** the minute runs on while they are away, the shelter its tries keep ends: a join within it shelters them again */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (retries.containsKey(id)) endShelter(id);
    }

    /**
     * died on the tour, or while their return was tried again: they respawn where they stood. Into a world that is not
     * loaded they respawn as usual and a running minute of tries ends; on the tour none runs yet, the tour takes them
     * along and its own return at the end starts it.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        int before = homes.size();
        Home home = homes.get(id);
        World world = home == null ? null : Bukkit.getWorld(home.world());
        if (world != null) {
            extinguish(p);
            e.setRespawnLocation(home.in(world));
            returned(p, false);   // still where they died: tick looks where they respawned
        } else if (home != null && !dropIfGone(p, home)) {
            endMinute(id);
            leaveToTheJoin(p, notLoaded(p, home), null);
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

    /** the ways back and the landing players; false (one warning ending in {@code consequence}) when it could not be written */
    private boolean save(String consequence) {
        Map<String, Object> out = new LinkedHashMap<>();
        homes.forEach((id, home) -> out.put(id.toString(), home.encode()));
        if (!landing.isEmpty()) out.put(LANDING, landing.keySet().stream().map(UUID::toString).toList());
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
