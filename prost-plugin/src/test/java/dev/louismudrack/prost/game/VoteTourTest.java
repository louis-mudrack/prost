package dev.louismudrack.prost.game;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.menu.GameMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PigMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The build contest's tour in a round of three on a clock the test moves by hand: each player marked a build and went
 * home before the building ended, so home and build differ. Everyone visits each build and always comes back.
 */
class VoteTourTest {
    private static final String STATUE = "vote_statue", RETURNS = "returns.yml";
    /** more steps than a tour of three builds can take */
    private static final int TOUR_STEPS = 10;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host, anna, ben;
    private List<PlayerMock> everyone;
    private final Map<PlayerMock, Location> homes = new LinkedHashMap<>(), builds = new LinkedHashMap<>();
    private Round round;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        plugin.clock(() -> now.get());
        host = server.addPlayer("Host");
        anna = server.addPlayer("Anna");
        ben = server.addPlayer("Ben");
        everyone = List.of(host, anna, ben);
        host.performCommand("prost start");
        round = plugin.rounds().of(host.getUniqueId());
        for (PlayerMock p : List.of(anna, ben)) p.performCommand("prost join " + round.code);
        int i = 1;
        for (PlayerMock p : everyone) {
            builds.put(p, at(10 * i, 64, 10 * i));
            homes.put(p, new Location(world(), -10.5 * i, 70, -10.5 * i, 45f * i, 10f));
            i++;
        }
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    // ---------------------------------------------------------------------------------------------- driving the contest

    /** the contest, every build marked by its builder, everyone back home: the building time still runs */
    private ChallengeRun build() {
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + round.code + " " + STATUE + " vote");
        ChallengeRun run = plugin.director().running(round);
        assertNotNull(run, STATUE + " runs as vote");
        tick();   // the first poll tells the buttons
        for (PlayerMock p : everyone) {
            p.teleport(builds.get(p));
            p.performCommand("prost here");
            p.teleport(homes.get(p));
        }
        return run;
    }

    /** the building time is up: the tour starts */
    private ChallengeRun tour() {
        ChallengeRun run = build();
        later(run.def.seconds() * 1000L);
        tick();
        return run;
    }

    /** build after build until {@code voter}'s vote menu opens */
    private void toVote(PlayerMock voter) {
        for (int i = 0; i < TOUR_STEPS && !(holder(voter) instanceof GameMenu); i++) {
            later(viewMs());
            tick();
        }
        assertInstanceOf(GameMenu.class, holder(voter), "the vote after the tour");
    }

    private long viewMs() { return plugin.cfg().games().voteViewSeconds() * 1000L; }

    private void later(long ms) { now.addAndGet(ms); }

    private void tick() { plugin.director().tick(); }

    private World world() { return server.getWorld("world"); }

    private Location at(double x, double y, double z) { return new Location(world(), x, y, z); }

    private static InventoryHolder holder(PlayerMock p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder(false);
    }

    private static boolean told(List<String> lines, String part) { return lines.stream().anyMatch(m -> m.contains(part)); }

    private File returnsFile() { return new File(plugin.getDataFolder(), RETURNS); }

    /** returns.yml as it is on disk: player id -> where they go back to */
    private Map<?, ?> saved() throws IOException {
        Object parsed = new Yaml().load(Files.readString(returnsFile().toPath(), StandardCharsets.UTF_8));
        return parsed instanceof Map<?, ?> m ? m : Map.of();
    }

    private void assertHome(PlayerMock p, String why) { assertEquals(homes.get(p), p.getLocation(), p.getName() + ": " + why); }

    /** the same place, also when its world was loaded again (another World object) */
    private static void assertSamePlace(Location expected, Location actual, String why) {
        assertEquals(expected.getWorld().getName(), actual.getWorld().getName(), why + ": world");
        assertEquals(List.of(expected.getX(), expected.getY(), expected.getZ(), (double) expected.getYaw(), (double) expected.getPitch()),
                List.of(actual.getX(), actual.getY(), actual.getZ(), (double) actual.getYaw(), (double) actual.getPitch()), why);
    }

    // ---------------------------------------------------------------------------------------------- the tour

    @Test
    void theTourVisitsEachBuildAndReturnsEveryone() {
        tour();
        Set<Location> visited = new HashSet<>();
        for (int build = 0; build < builds.size(); build++) {
            Location at = host.getLocation();
            assertTrue(builds.containsValue(at), "at a build: " + at);
            for (PlayerMock p : everyone) assertEquals(at, p.getLocation(), p.getName() + " tours with the others");
            assertTrue(visited.add(at), "each build once: " + at);
            later(viewMs() - 1000L);
            tick();
            for (PlayerMock p : everyone) assertEquals(at, p.getLocation(), "a build is on show for vote.view-seconds");
            later(1000L);
            tick();
        }
        assertEquals(new HashSet<>(builds.values()), visited);
        for (PlayerMock p : everyone) assertHome(p, "back where they stood");
        assertInstanceOf(GameMenu.class, holder(host), "the vote follows the tour");
    }

    @Test
    void aBuriedSpotIsRaisedAndAnUnreachableOneSkipped() {
        ChallengeRun run = build();
        world().getBlockAt(10, 64, 10).setType(Material.STONE);   // the host's build: the feet in stone
        world().getBlockAt(20, 65, 20).setType(Material.STONE);   // Anna's: the head in stone
        for (int y = 64; y <= 80; y++) world().getBlockAt(30, y, 30).setType(Material.STONE);   // Ben's: 17 blocks of stone
        TestServer.messages(host);
        later(run.def.seconds() * 1000L);
        tick();
        long tourStart = now.get();
        List<Location> visited = new ArrayList<>();
        List<String> told = new ArrayList<>();
        for (int i = 0; i < TOUR_STEPS && !(holder(host) instanceof GameMenu); i++) {
            visited.add(host.getLocation());
            told.addAll(TestServer.messages(host));
            later(viewMs());
            tick();
        }
        told.addAll(TestServer.messages(host));
        assertEquals(List.of(at(10, 65, 10), at(20, 66, 20)), visited.stream().sorted((a, b) -> Double.compare(a.getX(), b.getX())).toList(),
                "one block up out of the stone, two to get the head free");
        assertTrue(told(told, "Build by Ben: can't be visited (no room to stand within 16 blocks above the spot)"), told.toString());
        assertEquals(2 * viewMs(), now.get() - tourStart, "no viewing time for a build that cannot be visited");
        assertInstanceOf(GameMenu.class, holder(host));
        assertTrue(told(told, "Build by Host: world 10 64 10"), "the spot itself is announced: " + told);
    }

    @Test
    void touringPlayersTakeNoDamageAndCannotBuild() {
        PlayerMock outsider = server.addPlayer("Dora");
        tour();
        assertSheltered(anna, true, "on the tour");
        assertSheltered(outsider, false, "not in the contest");
        later(viewMs());
        tick();
        assertTrue(builds.containsValue(anna.getLocation()), "at the second build: " + anna.getLocation());
        assertSheltered(anna, true, "at the second build, long after the tour began");
        toVote(host);
        later(2000L);
        assertSheltered(anna, true, "2 s after the return");
        later(2000L);
        assertSheltered(anna, false, "4 s after the return");
    }

    private void assertSheltered(PlayerMock p, boolean sheltered, String when) {
        EntityDamageEvent hurt = new LivingEntitySimulation(p).simulateDamage(2, DamageSource.builder(DamageType.FALL).build());
        assertEquals(sheltered, hurt.isCancelled(), when + ": damage");
        Block floor = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
        floor.setType(Material.STONE);
        PlayerSimulation acts = new PlayerSimulation(p);
        assertEquals(sheltered, acts.simulateBlockBreak(floor).isCancelled(), when + ": breaking");
        assertEquals(sheltered, acts.simulateBlockPlace(Material.DIRT, p.getLocation().add(2, 0, 0)).isCancelled(), when + ": placing");
        PlayerBucketEmptyEvent bucket = new PlayerBucketEmptyEvent(p, floor.getRelative(BlockFace.UP), floor, BlockFace.UP,
                Material.WATER_BUCKET, new ItemStack(Material.WATER_BUCKET), EquipmentSlot.HAND);
        server.getPluginManager().callEvent(bucket);
        assertEquals(sheltered, bucket.isCancelled(), when + ": emptying a bucket");
    }

    /** the shelter cancels the burning, not the fire ticks: at home they would burn once the grace is over */
    @Test
    void fireAndFrostFromABuildDoNotFollowThemHome() {
        tour();
        anna.setFireTicks(200);
        anna.setFreezeTicks(100);
        toVote(host);
        assertHome(anna, "back");
        assertEquals(0, anna.getFireTicks(), "not burning at home");
        assertEquals(0, anna.getFreezeTicks(), "not freezing at home");
    }

    /** nothing at a build may be used, hit, broken, taken or left there */
    @Test
    void touringPlayersCannotUseOrTakeAnything() {
        tour();
        assertNothingTouched(anna, true, "on the tour");
        toVote(host);
        later(4000L);
        assertNothingTouched(anna, false, "4 s after the return");
    }

    private void assertNothingTouched(PlayerMock p, boolean sheltered, String when) {
        Location at = p.getLocation();
        World w = at.getWorld();
        Block chest = at.getBlock().getRelative(BlockFace.NORTH);
        chest.setType(Material.CHEST);
        PlayerInteractEvent use = new PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.FLINT_AND_STEEL), chest,
                BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(use);
        assertEquals(sheltered, use.useInteractedBlock() == Event.Result.DENY && use.useItemInHand() == Event.Result.DENY,
                when + ": a chest, flint and steel");
        PlayerInteractEvent air = new PlayerInteractEvent(p, Action.RIGHT_CLICK_AIR, new ItemStack(Material.ENDER_PEARL), null, BlockFace.UP,
                EquipmentSlot.HAND);
        server.getPluginManager().callEvent(air);
        assertEquals(sheltered, air.useItemInHand() == Event.Result.DENY, when + ": a pearl thrown into the air");
        PigMock pig = (PigMock) w.spawnEntity(at, EntityType.PIG);
        ItemFrame frame = w.spawn(at, ItemFrame.class);
        Arrow arrow = w.spawn(at, Arrow.class);
        arrow.setShooter(p);
        Item dirt = w.dropItem(at, new ItemStack(Material.DIRT));
        LivingEntitySimulation hurt = new LivingEntitySimulation(pig);
        assertEquals(sheltered, hurt.simulateDamage(2, p).isCancelled(), when + ": hitting an animal");
        assertEquals(sheltered, hurt.simulateDamage(2, arrow).isCancelled(), when + ": shooting an animal");
        Map<String, Event> touches = new LinkedHashMap<>();
        touches.put("using an animal", new PlayerInteractEntityEvent(p, pig, EquipmentSlot.HAND));
        touches.put("using an item frame", new PlayerInteractAtEntityEvent(p, frame, new Vector(), EquipmentSlot.HAND));
        touches.put("breaking an item frame", new HangingBreakByEntityEvent(frame, p, HangingBreakEvent.RemoveCause.ENTITY));
        touches.put("filling a bucket", new PlayerBucketFillEvent(p, chest, chest, BlockFace.UP, Material.BUCKET, new ItemStack(Material.BUCKET),
                EquipmentSlot.HAND));
        touches.put("dropping an item", new PlayerDropItemEvent(p, dirt));
        touches.put("picking up an item", new EntityPickupItemEvent(p, dirt, 0));
        touches.forEach((what, e) -> {
            server.getPluginManager().callEvent(e);
            assertEquals(sheltered, ((Cancellable) e).isCancelled(), when + ": " + what);
        });
    }

    @Test
    void aPlayerWhoQuitsMidTourIsReturnedOnJoin() throws Exception {
        tour();
        assertNotEquals(homes.get(anna), anna.getLocation(), "Anna is at a build");
        anna.disconnect();
        plugin.rounds().tickPresence();   // she is away now: the run drops her while she is offline
        tick();
        toVote(host);
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "her way back waits for her: " + saved());
        anna.reconnect();
        assertHome(anna, "returned on joining");
        assertEquals(Map.of(), saved());
    }

    @Test
    void returnsSurviveACrash() throws Exception {
        tour();
        String onDisk = Files.readString(returnsFile().toPath(), StandardCharsets.UTF_8);
        Map<?, ?> saved = saved();
        for (PlayerMock p : everyone) assertTrue(saved.containsKey(p.getUniqueId().toString()), "written when the tour starts: " + onDisk);
        Location home = homes.get(anna);
        PlayerMock old = anna;
        // a crash: the old instance never stops, what is left is the file as it was
        MockBukkit.unmock();
        server = TestServer.mock();
        plugin = TestServer.start(server, folder -> {
            try {
                Files.createDirectories(folder.toPath());
                Files.writeString(new File(folder, RETURNS).toPath(), onDisk, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        PlayerMock again = new PlayerMock(server, old.getName(), old.getUniqueId());
        server.addPlayer(again);
        assertSamePlace(home, again.getLocation(), "returned on joining the restarted server");
        assertFalse(saved().containsKey(old.getUniqueId().toString()), "and forgotten");
    }

    @Test
    void teleportOffOnlyAnnounces() throws Exception {
        File file = new File(plugin.getDataFolder(), "game.yml");
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        c.set("vote.teleport", false);
        c.save(file);
        plugin.reloadData();
        ChallengeRun run = build();
        TestServer.messages(host);
        later(run.def.seconds() * 1000L);
        tick();
        List<String> told = new ArrayList<>(TestServer.messages(host));
        for (int i = 0; i < TOUR_STEPS && !(holder(host) instanceof GameMenu); i++) {
            for (PlayerMock p : everyone) assertHome(p, "nobody is moved");
            later(viewMs());
            tick();
            told.addAll(TestServer.messages(host));
        }
        for (PlayerMock p : everyone) assertHome(p, "nobody is moved");
        assertTrue(told(told, "Build by Anna: world 20 64 20"), "the builds are announced: " + told);
        assertFalse(returnsFile().exists() && !saved().isEmpty(), "no way back to keep");
    }

    // ---------------------------------------------------------------------------------------------- every way out of a tour

    @Test
    void aPlayerWhoDropsOutIsBackAtOnce() {
        tour();
        ben.performCommand("prost pause");
        tick();
        assertHome(ben, "back as soon as he is out");
        later(viewMs());
        tick();
        assertHome(ben, "the tour goes on without him");
        assertNotEquals(homes.get(anna), anna.getLocation(), "the others tour on");
    }

    /** once returns.yml is blocked by a folder every save fails with one warning: so the warnings count the saves */
    @Test
    void theEndOfTheTourSavesTheWaysBackOnceForEveryone() {
        tour();
        assertTrue(returnsFile().delete());
        assertTrue(new File(returnsFile(), "in-the-way").mkdirs(), "a folder where returns.yml belongs");
        try (LogCapture log = LogCapture.root()) {
            toVote(host);
            assertEquals(1, log.messages().stream().filter(m -> m.contains(RETURNS + " not saved")).count(),
                    "one save for the three returns: " + log.messages());
        }
        for (PlayerMock p : everyone) assertHome(p, "back where they stood");
    }

    @Test
    void aContestCancelledMidTourBringsEveryoneBack() throws Exception {
        tour();
        anna.performCommand("prost leave");
        ben.performCommand("prost leave");
        tick();
        assertNull(plugin.director().running(round), "too few left: cancelled");
        for (PlayerMock p : everyone) assertHome(p, "back after the cancel");
        assertEquals(Map.of(), saved());
    }

    @Test
    void aPlayerWhoDiesOnTheTourRespawnsWhereTheyStood() {
        tour();
        anna.setFireTicks(200);
        anna.setHealth(0);
        assertTrue(anna.isDead());
        anna.respawn();
        assertHome(anna, "respawned where she stood");
        assertEquals(0, anna.getFireTicks(), "not burning");
        later(viewMs());
        tick();
        assertHome(anna, "Prost holds no way back for her any more, so the tour does not take her along");
    }

    @Test
    void aRiderLeavesTheVehicleFirst() {
        build();
        Entity pig = world().spawnEntity(homes.get(anna), EntityType.PIG);
        pig.addPassenger(anna);
        assertTrue(anna.isInsideVehicle());
        later(plugin.director().running(round).def.seconds() * 1000L);
        tick();
        assertFalse(anna.isInsideVehicle());
        assertTrue(builds.containsValue(anna.getLocation()), "on the tour: " + anna.getLocation());
    }

    @Test
    void nobodyMovesWhenTheWayBackCannotBeSaved() throws Exception {
        ChallengeRun run = build();
        File blocked = returnsFile();
        assertTrue(new File(blocked, "in-the-way").mkdirs(), "a folder where returns.yml belongs");
        TestServer.messages(host);
        try (LogCapture log = LogCapture.root()) {
            later(run.def.seconds() * 1000L);
            tick();
            assertEquals(1, log.messages().stream().filter(m -> m.contains(RETURNS)).count(), "one warning: " + log.messages());
        }
        for (PlayerMock p : everyone) assertHome(p, "nobody moves without a saved way back");
        assertTrue(told(TestServer.messages(host), "Build by "), "the tour only announces");
    }

    /** Anna went home to the world "other", which unloads during the tour */
    private void tourFromAnotherWorld(boolean folderStays) {
        server.addSimpleWorld("other");
        if (folderStays) assertTrue(new File(server.getWorldContainer(), "other").mkdirs());
        homes.put(anna, new Location(server.getWorld("other"), 5.5, 70, 5.5, 30f, 0f));
        tour();
        assertTrue(server.unloadWorld("other", false), "the world unloads");
    }

    @Test
    void aWayBackIntoAnUnloadedWorldWaitsForTheNextJoin() throws Exception {
        tourFromAnotherWorld(true);
        Location home = homes.get(anna);
        TestServer.messages(anna);
        try (LogCapture log = LogCapture.root()) {
            toVote(host);
            assertEquals(1, log.messages().stream().filter(m -> m.contains("Anna") && m.contains("not loaded")).count(), log.messages().toString());
        }
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"), "she is told too");
        assertTrue(builds.containsValue(anna.getLocation()), "no other place instead: " + anna.getLocation());
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept");
        server.addSimpleWorld("other");
        anna.disconnect();
        anna.reconnect();
        assertSamePlace(home, anna.getLocation(), "the world is back: so is she");
        assertFalse(saved().containsKey(anna.getUniqueId().toString()));
    }

    @Test
    void aWayBackIntoAWorldThatIsGoneIsDropped() throws Exception {
        tourFromAnotherWorld(false);
        TestServer.messages(anna);
        try (LogCapture log = LogCapture.root()) {
            toVote(host);
            assertEquals(1, log.messages().stream().filter(m -> m.contains("Anna") && m.contains("no longer exists")).count(), log.messages().toString());
        }
        assertTrue(told(TestServer.messages(anna), "no longer exists - you stay here"), "she is told too");
        assertFalse(saved().containsKey(anna.getUniqueId().toString()), "dropped");
        assertSheltered(anna, false, "no way back, no tour: no shelter");
        Location stays = anna.getLocation();
        anna.disconnect();
        anna.reconnect();
        assertEquals(stays, anna.getLocation(), "nothing left to apply on joining");
    }

    /** dropped on the respawn while the tour still runs: the shelter ends with the way back, it does not last forever */
    @Test
    void aWayBackDroppedOnRespawnEndsTheShelter() {
        tourFromAnotherWorld(false);
        anna.setHealth(0);
        anna.respawn();
        assertSheltered(anna, false, "her way back is gone");
        toVote(host);
        later(4000L);
        assertSheltered(anna, false, "after the tour");
    }

    /** another plugin refuses the teleport home */
    public static final class HomeBlocker implements Listener {
        private final Location home;

        HomeBlocker(Location home) { this.home = home; }

        @EventHandler
        public void on(PlayerTeleportEvent e) { if (home.equals(e.getTo())) e.setCancelled(true); }
    }

    @Test
    void aRefusedReturnIsToldAndTriedAgainOnJoin() throws Exception {
        tour();
        HomeBlocker blocker = new HomeBlocker(homes.get(anna));
        server.getPluginManager().registerEvents(blocker, plugin);
        TestServer.messages(anna);
        toVote(host);
        assertTrue(builds.containsValue(anna.getLocation()), "still at a build: " + anna.getLocation());
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"));
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept");
        assertSheltered(anna, false, "the tour is over for her");
        HandlerList.unregisterAll(blocker);
        anna.disconnect();
        anna.reconnect();
        assertHome(anna, "back on joining");
    }
}
