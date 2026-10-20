package dev.louismudrack.prost.game;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.ProstApi;
import dev.louismudrack.prost.menu.GameMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
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
import org.bukkit.inventory.meta.SkullMeta;
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
import org.mockbukkit.mockbukkit.world.WorldMock;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
    private PlayerMock host, ben;
    private FragileChatPlayer anna;
    private List<PlayerMock> everyone;
    private final Map<PlayerMock, Location> homes = new LinkedHashMap<>(), builds = new LinkedHashMap<>();
    private Round round;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        plugin.clock(() -> now.get());
        host = server.addPlayer("Host");
        anna = new FragileChatPlayer(server, "Anna");
        server.addPlayer(anna);
        ben = server.addPlayer("Ben");
        everyone = List.of(host, anna, ben);
        host.performCommand("prost start");
        round = plugin.rounds().of(host.getUniqueId());
        for (PlayerMock p : List.of(anna, ben)) p.performCommand("prost join " + round.code);
        int i = 1;
        for (PlayerMock p : everyone) {
            builds.put(p, at(10 * i, 64, 10 * i));
            homes.put(p, new Location(world(), -10.5 * i, 70, -10.5 * i, 45f * i, 10f));
            floorOf(homes.get(p)).setType(Material.STONE);   // a return home is no fall
            homes.get(p).getChunk().load();   // as a player's chunk is on a server: MockBukkit loads one only when asked
            i++;
        }
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    /** a message containing {@code chatBreaksOn} (null: none) throws, as a broken placeholder in a lang text would */
    static final class FragileChatPlayer extends PlayerMock {
        String chatBreaksOn;

        FragileChatPlayer(ServerMock server, String name) { super(server, name); }

        @Override
        public void sendMessage(Component message) {
            if (chatBreaksOn != null && PlainTextComponentSerializer.plainText().serialize(message).contains(chatBreaksOn))
                throw new IllegalStateException("a broken chat");
            super.sendMessage(message);
        }
    }

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

    /** the block someone at {@code at} stands on */
    private static Block floorOf(Location at) { return at.getBlock().getRelative(BlockFace.DOWN); }

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

    private void teleportOff() throws IOException {
        File file = new File(plugin.getDataFolder(), "game.yml");
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        c.set("vote.teleport", false);
        c.save(file);
        plugin.reloadData();
    }

    /** Ben built in the Nether, which the server closes before the tour (as AniOps' /ani survival nether close does) */
    private ChallengeRun benBuildsInTheNetherThatCloses() {
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        AtomicBoolean gateOpen = new AtomicBoolean(true);
        server.getServicesManager().load(ProstApi.class).registerDimensionGate(MockBukkit.createMockPlugin("Network"), env -> gateOpen.get());
        builds.put(ben, new Location(nether, 30, 64, 30));
        ChallengeRun run = build();
        gateOpen.set(false);
        return run;
    }

    /** the builders in {@code viewer}'s vote menu: MockBukkit's skull keeps the owner's name */
    private static Set<String> votable(PlayerMock viewer) {
        Set<String> out = new HashSet<>();
        for (ItemStack it : viewer.getOpenInventory().getTopInventory().getContents())
            if (it != null && it.getItemMeta() instanceof SkullMeta skull && skull.getOwningPlayer() != null) out.add(skull.getOwningPlayer().getName());
        return out;
    }

    @Test
    void aBuildInAClosedDimensionIsNotVisitedButStaysVotable() {
        ChallengeRun run = benBuildsInTheNetherThatCloses();
        TestServer.messages(host);
        later(run.def.seconds() * 1000L);
        tick();
        long tourStart = now.get();
        Set<Location> visited = new HashSet<>();
        List<String> told = new ArrayList<>();
        for (int i = 0; i < TOUR_STEPS && !(holder(host) instanceof GameMenu); i++) {
            visited.add(host.getLocation());
            told.addAll(TestServer.messages(host));
            later(viewMs());
            tick();
        }
        told.addAll(TestServer.messages(host));
        assertEquals(Set.of(builds.get(host), builds.get(anna)), visited, "only the builds outside the closed Nether");
        assertTrue(told(told, "Build by Ben: can't be visited (its dimension is closed), it still gets votes"), told.toString());
        assertEquals(2 * viewMs(), now.get() - tourStart, "no viewing time for it");
        for (PlayerMock p : everyone) assertHome(p, "back where they stood");
        assertEquals(Set.of("Anna", "Ben"), votable(host), "it stays votable");
    }

    @Test
    void withoutTeleportABuildInAClosedDimensionIsStillAnnounced() throws Exception {
        teleportOff();
        ChallengeRun run = benBuildsInTheNetherThatCloses();
        TestServer.messages(host);
        later(run.def.seconds() * 1000L);
        tick();
        List<String> told = new ArrayList<>(TestServer.messages(host));
        for (int i = 0; i < TOUR_STEPS && !(holder(host) instanceof GameMenu); i++) {
            later(viewMs());
            tick();
            told.addAll(TestServer.messages(host));
        }
        assertTrue(told(told, "Build by Ben: world_nether 30 64 30"), "its coordinates are named: " + told);
        assertFalse(told(told, "its dimension is closed"), told.toString());
    }

    @Test
    void teleportOffOnlyAnnounces() throws Exception {
        teleportOff();
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
        assertTrue(told(TestServer.messages(anna), "Prost tries again for a minute"), "she is told too");
        assertTrue(builds.containsValue(anna.getLocation()), "no other place instead: " + anna.getLocation());
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept");
        later(60_001L);
        plugin.returns().tick();
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"));
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
        assertTrue(told(TestServer.messages(anna), "Prost tries again for a minute"));
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept");
        assertSheltered(anna, true, "while her return is tried again");
        later(60_001L);
        plugin.returns().tick();
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"));
        assertSheltered(anna, false, "the tries are over");
        HandlerList.unregisterAll(blocker);
        anna.disconnect();
        anna.reconnect();
        assertHome(anna, "back on joining");
    }

    @Test
    void aRefusedReturnIsRetriedWhileOnline() throws Exception {
        tour();
        HomeBlocker blocker = new HomeBlocker(homes.get(anna));
        server.getPluginManager().registerEvents(blocker, plugin);
        TestServer.messages(anna);
        toVote(host);
        assertTrue(builds.containsValue(anna.getLocation()), "still at a build: " + anna.getLocation());
        assertTrue(told(TestServer.messages(anna), "Prost tries again for a minute"));
        assertSheltered(anna, true, "while her return is tried again");
        HandlerList.unregisterAll(blocker);
        later(5000L);
        plugin.returns().tick();
        assertHome(anna, "back on the next try");
        assertTrue(told(TestServer.messages(anna), "You are back where you stood before the tour"));
        assertFalse(saved().containsKey(anna.getUniqueId().toString()), "forgotten");
        later(60_001L);
        plugin.returns().tick();
        assertFalse(told(TestServer.messages(anna), "Prost brings you back when you join again"), "no tries left to give up");
    }

    @Test
    void aReturnThatKeepsFailingGivesUpAfterAMinute() throws Exception {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), plugin);
        toVote(host);
        TestServer.messages(anna);
        later(5000L);
        plugin.returns().tick();
        assertTrue(builds.containsValue(anna.getLocation()), "refused again: " + anna.getLocation());
        assertEquals(List.of(), TestServer.messages(anna), "a try that fails again is not told again");
        later(55_001L);
        plugin.returns().tick();
        assertSheltered(anna, false, "a minute after the tour");
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"));
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept for the join");
    }

    @Test
    void aPendingRetryEndsWithTheRespawnReturn() {
        tour();
        HomeBlocker blocker = new HomeBlocker(homes.get(anna));
        server.getPluginManager().registerEvents(blocker, plugin);
        toVote(host);
        anna.setHealth(0);
        HandlerList.unregisterAll(blocker);
        anna.respawn();
        assertHome(anna, "respawned where she stood");
        Location walkedOff = at(0, 64, 0);
        anna.teleport(walkedOff);
        TestServer.messages(anna);
        later(5000L);
        plugin.returns().tick();
        assertEquals(walkedOff, anna.getLocation(), "no try moves her again");
        later(60_001L);
        plugin.returns().tick();
        assertFalse(told(TestServer.messages(anna), "Prost brings you back when you join again"), "no tries left to give up");
    }

    /** a stop brings the tour back inline: no tick tries again after it, the join does */
    @Test
    void aReturnRefusedAtAStopIsLeftToTheJoin() throws Exception {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), MockBukkit.createMockPlugin("Blocker"));
        TestServer.messages(anna);
        server.getPluginManager().disablePlugin(plugin);
        List<String> told = TestServer.messages(anna);
        assertTrue(told(told, "Prost brings you back when you join again"), told.toString());
        assertFalse(told(told, "Prost tries again for a minute"), told.toString());
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept for the join");
    }

    /** another plugin's listener throws on the teleport home: MockBukkit passes it on, as an error inside Prost would come */
    public static final class HomeBreaker implements Listener {
        private final Location home;

        HomeBreaker(Location home) { this.home = home; }

        @EventHandler
        public void on(PlayerTeleportEvent e) { if (home.equals(e.getTo())) throw new IllegalStateException("a broken listener"); }
    }

    @Test
    void aReturnThatThrowsIsRetriedLikeARefusedOne() throws Exception {
        tour();
        server.getPluginManager().registerEvents(new HomeBreaker(homes.get(anna)), plugin);
        HomeBlocker bensBlocker = new HomeBlocker(homes.get(ben));
        server.getPluginManager().registerEvents(bensBlocker, plugin);
        TestServer.messages(anna);
        try (LogCapture log = LogCapture.root()) {
            toVote(host);
            HandlerList.unregisterAll(bensBlocker);
            later(5000L);
            plugin.returns().tick();
            assertEquals(1, log.messages().stream().filter(m -> m.contains("Anna could not be brought back")).count(),
                    "logged once: " + log.messages());
        }
        assertHome(ben, "his try is not stopped by her error");
        assertHome(host, "the others are back");
        assertTrue(told(TestServer.messages(anna), "Prost tries again for a minute"));
        assertSheltered(anna, true, "while her return is tried again");
        later(55_001L);
        plugin.returns().tick();
        assertSheltered(anna, false, "the minute is over");
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"));
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept for the join");
    }

    /** she mounted something to ride home herself: the tries leave her on it, the minute runs on */
    @Test
    void aRetryDoesNotThrowAStrandedRiderOff() {
        tour();
        HomeBlocker blocker = new HomeBlocker(homes.get(anna));
        server.getPluginManager().registerEvents(blocker, plugin);
        toVote(host);
        world().spawnEntity(anna.getLocation(), EntityType.PIG).addPassenger(anna);
        HandlerList.unregisterAll(blocker);
        later(5000L);
        plugin.returns().tick();
        assertTrue(anna.isInsideVehicle(), "still riding");
        anna.leaveVehicle();
        plugin.returns().tick();
        assertHome(anna, "the next try, once she is off");
    }

    /** the minute belongs to the way back: no join after it starts another one */
    @Test
    void aJoinAfterTheMinuteTriesOnceWithoutProtection() throws Exception {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), plugin);
        toVote(host);
        later(60_001L);
        plugin.returns().tick();
        for (int join = 1; join <= 2; join++) {
            anna.disconnect();
            TestServer.messages(anna);
            try (LogCapture log = LogCapture.root()) {
                anna.reconnect();
                assertEquals(1, log.messages().stream().filter(m -> m.contains("Anna could not be moved back")
                        && m.endsWith(" - tried again when they join")).count(), "join " + join + ": " + log.messages());
            }
            List<String> told = TestServer.messages(anna);
            assertTrue(told(told, "Prost brings you back when you join again"), "join " + join + ": " + told);
            assertFalse(told(told, "Prost tries again for a minute"), "join " + join + ": " + told);
            assertSheltered(anna, false, "join " + join + " after the minute");
            assertTrue(builds.containsValue(anna.getLocation()), "still at a build: " + anna.getLocation());
            assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept for the next join");
        }
    }

    @Test
    void aJoinWithinTheMinuteKeepsItsEnd() {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), plugin);
        toVote(host);
        later(20_000L);
        anna.disconnect();
        TestServer.messages(anna);
        anna.reconnect();
        assertTrue(told(TestServer.messages(anna), "Prost tries again for a minute"), "told again on joining");
        assertSheltered(anna, true, "rejoined within the minute");
        later(40_001L);
        plugin.returns().tick();
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"), "a minute after the first failure");
        assertSheltered(anna, false, "the minute is over");
    }

    @Test
    void aMinuteThatRunsOutWhileAwayIsOverOnJoining() { minuteRunsOutWhileAway(true); }

    /** no tick saw the end of the minute: the join finds it itself */
    @Test
    void aMinuteThatRunsOutWhileAwayIsOverOnJoiningBeforeTickSeesIt() { minuteRunsOutWhileAway(false); }

    private void minuteRunsOutWhileAway(boolean tickSeesIt) {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), plugin);
        toVote(host);
        later(20_000L);
        anna.disconnect();
        later(40_001L);
        if (tickSeesIt) plugin.returns().tick();
        TestServer.messages(anna);
        anna.reconnect();
        List<String> told = TestServer.messages(anna);
        assertTrue(told(told, "Prost brings you back when you join again"), told.toString());
        assertFalse(told(told, "Prost tries again for a minute"), told.toString());
        assertSheltered(anna, false, "the minute ran out while she was away");
    }

    @Test
    void aJoinAfterARespawnIntoAnUnloadedWorldIsNotProtected() {
        tourFromAnotherWorld(true);
        toVote(host);
        assertTrue(told(TestServer.messages(anna), "Prost tries again for a minute"), "the end of the tour starts her minute");
        anna.setHealth(0);
        anna.respawn();
        assertTrue(told(TestServer.messages(anna), "Prost brings you back when you join again"), "the respawn ends her minute");
        anna.disconnect();
        anna.reconnect();
        List<String> told = TestServer.messages(anna);
        assertTrue(told(told, "Prost brings you back when you join again"), told.toString());
        assertFalse(told(told, "Prost tries again for a minute"), told.toString());
        assertSheltered(anna, false, "the respawn ended the minute");
    }

    /** no minute runs while she is on the tour: the respawn ends none, the tour's own return starts it */
    @Test
    void aPlayerWhoDiesOnTheTourStillGetsTheMinuteAtItsEnd() {
        tourFromAnotherWorld(true);
        anna.setHealth(0);
        anna.respawn();
        TestServer.messages(anna);
        toVote(host);
        assertTrue(told(TestServer.messages(anna), "Prost tries again for a minute"), "the end of the tour starts her minute");
        assertSheltered(anna, true, "while her return is tried again");
    }

    @Test
    void aFinishedReturnWhoseMessageFailsIsNotTriedAgain() throws Exception {
        tour();
        anna.chatBreaksOn = "You are back where you stood";
        try (LogCapture log = LogCapture.root()) {
            toVote(host);
            assertHome(anna, "back, although she could not be told");
            assertSheltered(anna, true, "the grace after the return holds, although telling her failed");
            List<LogRecord> back = log.records().stream().filter(r -> r.getMessage().contains("Anna is back from the tour")).toList();
            assertEquals(1, back.size(), log.messages().toString());
            assertEquals(Level.WARNING, back.get(0).getLevel());
            assertEquals("a broken chat", back.get(0).getThrown().getMessage(), "with the stack trace");
            later(3001L);
            assertSheltered(anna, false, "the grace after the return only");
            log.clear();
            later(60_001L);
            plugin.returns().tick();
            assertEquals(List.of(), log.messages().stream().filter(m -> m.contains("Anna")).toList(), "no try of a finished return");
        }
        assertFalse(saved().containsKey(anna.getUniqueId().toString()), "forgotten");
    }

    @Test
    void oneFailingPlayerDoesNotStopTheOthersTries() {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), plugin);
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(ben)), plugin);
        toVote(host);
        anna.chatBreaksOn = "Prost brings you back when you join again";
        TestServer.messages(ben);
        try (LogCapture log = LogCapture.root()) {
            later(60_001L);
            plugin.returns().tick();
            assertEquals(1, log.messages().stream().filter(m -> m.contains("Anna's return could not be tried again")).count(),
                    log.messages().toString());
        }
        assertTrue(told(TestServer.messages(ben), "Prost brings you back when you join again"), "Ben's minute ends too");
        assertSheltered(ben, false, "the minute is over for him");
    }

    /** the error comes back to retry as a second failure of the same return: telling it again would throw past the others */
    @Test
    void aFailedReturnWhoseMessageFailsKeepsNobodyElseAway() {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), plugin);
        anna.chatBreaksOn = "Prost tries again for a minute";
        try (LogCapture log = LogCapture.root()) {
            toVote(host);
            assertEquals(1, log.messages().stream().filter(m -> m.contains("Anna could not be brought back")).count(),
                    log.messages().toString());
        }
        assertHome(ben, "brought back after her");
        assertSheltered(anna, true, "her minute of tries runs");
    }

    /** at a stop retry tells "join again" every time, so its error path throws once more: the loop takes it */
    @Test
    void oneReturnThatThrowsAtAStopKeepsNobodyElseAway() throws Exception {
        tour();
        server.getPluginManager().registerEvents(new HomeBlocker(homes.get(anna)), MockBukkit.createMockPlugin("Blocker"));
        anna.chatBreaksOn = "Prost brings you back when you join again";
        try (LogCapture log = LogCapture.root()) {
            server.getPluginManager().disablePlugin(plugin);
            List<LogRecord> kept = log.records().stream()
                    .filter(r -> r.getMessage().equals("Anna could not be brought back from the tour - their way back is kept")).toList();
            assertEquals(1, kept.size(), log.messages().toString());
            assertEquals(Level.WARNING, kept.get(0).getLevel());
            assertEquals("a broken chat", kept.get(0).getThrown().getMessage(), "with the stack trace");
        }
        assertHome(ben, "back after the stop");
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "kept for her join");
    }

    /** the scheduler runs Returns#tick: every other test calls it by hand */
    @Test
    void theSchedulerEndsALanding() {
        midAir(anna);
        tour();
        toVote(host);
        land(anna);
        server.getScheduler().performTicks(10);
        later(3001L);
        assertSheltered(anna, false, "the grace after landing is over");
    }

    // ---------------------------------------------------------------------------------------------- a return into mid-air

    /** nothing under {@code p}'s home: they were flying or falling there when the tour started */
    private void midAir(PlayerMock p) { floorOf(homes.get(p)).setType(Material.AIR); }

    /** something solid under {@code p}'s feet: they land (assertSheltered puts one there too) */
    private static void land(PlayerMock p) { floorOf(p.getLocation()).setType(Material.STONE); }

    @Test
    void aReturnToMidAirIsShelteredUntilLanding() {
        midAir(anna);
        anna.setFallDistance(12f);
        tour();
        toVote(host);
        assertHome(anna, "back in mid-air");
        assertEquals(0f, anna.getFallDistance(), "the fall before the tour does not count");
        later(4000L);
        plugin.returns().tick();
        assertSheltered(anna, true, "4 s after the return, still falling");
        land(anna);
        plugin.returns().tick();
        later(3001L);
        assertSheltered(anna, false, "the grace after landing is over");
    }

    @Test
    void aLandingShelterEndsAtTheCap() {
        midAir(anna);
        tour();
        toVote(host);
        later(29_999L);
        plugin.returns().tick();
        assertSheltered(anna, true, "falling for almost 30 s");
        land(anna);
        later(2L);
        plugin.returns().tick();
        assertSheltered(anna, false, "30 s after the return a landing brings no new grace");
    }

    @Test
    void aReturnedFallerWhoRejoinsIsStillSheltered() {
        midAir(anna);
        tour();
        toVote(host);
        anna.disconnect();
        later(5000L);
        plugin.returns().tick();
        anna.reconnect();
        plugin.returns().tick();
        assertSheltered(anna, true, "rejoined before landing");
        land(anna);
        plugin.returns().tick();
        later(3001L);
        assertSheltered(anna, false, "landed");
    }

    /** the cap counts while she is online: away longer than it, her landing starts again when she joins */
    @Test
    void aFallerWhoRejoinsAfterTheCapIsStillSheltered() {
        midAir(anna);
        tour();
        toVote(host);
        anna.disconnect();
        later(60_000L);
        plugin.returns().tick();
        anna.reconnect();
        later(4000L);
        plugin.returns().tick();
        assertSheltered(anna, true, "4 s after rejoining, a minute after the return");
        land(anna);
        plugin.returns().tick();
        later(3001L);
        assertSheltered(anna, false, "landed");
    }

    /**
     * a crash while Anna falls: the old instance never stops, what is left is returns.yml as it was. Prost starts on a
     * new server on the test's clock; {@code beforeProst} runs there first.
     */
    private void crashWhileAnnaFalls(Runnable beforeProst) throws IOException {
        midAir(anna);
        tour();
        toVote(host);
        assertEquals(List.of(anna.getUniqueId().toString()), saved().get("landing"), "her landing is in the file");
        String onDisk = Files.readString(returnsFile().toPath(), StandardCharsets.UTF_8);
        MockBukkit.unmock();
        server = TestServer.mock();
        plugin = TestServer.start(server, folder -> {
            try {
                Files.createDirectories(folder.toPath());
                Files.writeString(new File(folder, RETURNS).toPath(), onDisk, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            ((ProstPlugin) server.getPluginManager().getPlugin("Prost")).clock(() -> now.get());
            beforeProst.run();
        });
    }

    /** a player with Anna's name and id joins the new server where nothing is under her feet */
    private PlayerMock annaJoinsInMidAir() {
        Location midAir = at(0.5, 70, 0.5);
        midAir.getChunk().load();
        assertEquals(Material.AIR, floorOf(midAir).getType(), "nothing under her on the new server");
        PlayerMock again = new PlayerMock(server, anna.getName(), anna.getUniqueId());
        again.setLocation(midAir);
        server.addPlayer(again);
        assertEquals(midAir, again.getLocation(), "she joins in mid-air");
        return again;
    }

    /** sheltered while she falls, the grace once she lands, and then the landing is out of the file */
    private void assertResumedLanding(PlayerMock p) throws IOException {
        later(4000L);
        plugin.returns().tick();
        assertSheltered(p, true, "4 s after the landing resumed, still falling");
        land(p);
        plugin.returns().tick();
        later(3001L);
        assertSheltered(p, false, "the grace after landing is over");
        assertFalse(saved().containsKey("landing"), "her landing is over in the file too: " + saved());
    }

    @Test
    void aLandingCutShortByARestartResumesOnJoin() throws Exception {
        crashWhileAnnaFalls(() -> { });
        assertResumedLanding(annaJoinsInMidAir());
    }

    /** she was online when Prost stopped (a stop, a reload, a crash): its start finds her landing */
    @Test
    void aLandingIsResumedWhenProstStartsWhilePlayersAreOnline() throws Exception {
        AtomicReference<PlayerMock> again = new AtomicReference<>();
        crashWhileAnnaFalls(() -> again.set(annaJoinsInMidAir()));
        assertResumedLanding(again.get());
    }

    @Test
    void theFileListsNoLandingWhenNobodyLands() throws Exception {
        tour();
        toVote(host);
        assertEquals(Map.of(), saved(), "everyone is home on solid ground");
    }

    @Test
    void aNewTourEndsALandingInTheFileToo() throws Exception {
        midAir(anna);
        tour();
        toVote(host);
        assertTrue(plugin.returns().remember(Map.of(anna.getUniqueId(), anna.getLocation())));
        assertFalse(saved().containsKey("landing"), "on the next tour she is no longer landing: " + saved());
        assertTrue(saved().containsKey(anna.getUniqueId().toString()), "her new way back");
    }

    /** Anna swims at home, Ben stands in a waterlogged slab: nothing under either, but in water there is no fall */
    @Test
    void aReturnIntoWaterIsALanding() {
        midAir(anna);
        midAir(ben);
        homes.get(anna).getBlock().setType(Material.WATER);
        Block slab = homes.get(ben).getBlock();
        slab.setType(Material.OAK_SLAB);
        Waterlogged wet = (Waterlogged) slab.getBlockData();
        wet.setWaterlogged(true);
        slab.setBlockData(wet);
        tour();
        toVote(host);
        later(3001L);
        assertSheltered(anna, false, "the grace only, in water");
        assertSheltered(ben, false, "the grace only, in a waterlogged block");
    }

    /** Paper refreshes isInWater on the entity's next tick: right after the teleport home it still tells of the build */
    @Test
    void aMidAirReturnFromABuildInWaterKeepsTheLandingShelter() {
        midAir(anna);
        tour();
        anna.getLocation().getBlock().setType(Material.WATER);
        anna.setInWater(true);
        toVote(host);
        assertHome(anna, "back in mid-air");
        later(4000L);
        plugin.returns().tick();
        assertSheltered(anna, true, "4 s after the return, still falling");
        land(anna);
        plugin.returns().tick();
        later(3001L);
        assertSheltered(anna, false, "the grace after landing is over");
    }

    /** a flyer (creative, /fly) chooses when to come down */
    @Test
    void aFlyerIsNotHeldForTheLanding() {
        midAir(anna);
        anna.setAllowFlight(true);
        anna.setFlying(true);
        tour();
        toVote(host);
        assertHome(anna, "back in the air");
        later(3001L);
        plugin.returns().tick();
        assertSheltered(anna, false, "her flight held through the grace");
    }

    /** the flight that kept her up ends right after the return: she falls, and is held like any faller */
    @Test
    void aFlyerWhoseFlightEndsRightAfterTheReturnIsShelteredUntilLanding() {
        midAir(anna);
        anna.setAllowFlight(true);
        anna.setFlying(true);
        tour();
        toVote(host);
        anna.setFlying(false);
        later(4000L);
        plugin.returns().tick();
        assertSheltered(anna, true, "4 s after the return, falling since her flight ended");
        land(anna);
        plugin.returns().tick();
        later(3001L);
        assertSheltered(anna, false, "the grace after landing is over");
    }

    /** a floor Prost cannot read counts as none */
    @Test
    void aReturnWhereTheGroundCannotBeReadKeepsTheShelter() {
        tour();
        Chunk chunk = homes.get(anna).getChunk();
        assertTrue(world().unloadChunk(chunk.getX(), chunk.getZ(), false));
        toVote(host);
        later(4000L);
        plugin.returns().tick();
        assertSheltered(anna, true, "4 s after the return, the ground unread");
        chunk.load();
        plugin.returns().tick();
        later(3001L);
        assertSheltered(anna, false, "the grace once the ground is read");
    }

    /** the respawn event comes before the move: where she died says nothing about where she lands */
    @Test
    void aRespawnIntoMidAirIsShelteredUntilLanding() {
        midAir(anna);
        tour();
        land(anna);   // at the build
        anna.setHealth(0);
        anna.respawn();
        assertHome(anna, "respawned where she stood, in mid-air");
        later(4000L);
        plugin.returns().tick();
        assertSheltered(anna, true, "4 s after the respawn, still falling");
        land(anna);
        plugin.returns().tick();
        later(3001L);
        assertSheltered(anna, false, "the grace after landing is over");
    }

    // ---------------------------------------------------------------------------------------------- passengers, refused visits

    @Test
    void aPlayerCarryingAPassengerIsToured() {
        ChallengeRun run = build();
        anna.addPassenger(world().spawnEntity(homes.get(anna), EntityType.PIG));
        later(run.def.seconds() * 1000L);
        tick();
        assertTrue(builds.containsValue(anna.getLocation()), "on the tour: " + anna.getLocation());
        anna.addPassenger(world().spawnEntity(anna.getLocation(), EntityType.PIG));
        toVote(host);
        assertHome(anna, "back with nothing on her");
    }

    @Test
    void aRefusedVisitIsToldAndTheTourGoesOn() {
        ChallengeRun run = build();
        server.getPluginManager().registerEvents(new HomeBlocker(builds.get(ben)), plugin);   // the same refusal, at Ben's build
        TestServer.messages(anna);
        later(run.def.seconds() * 1000L);
        tick();
        List<String> told = new ArrayList<>();
        for (int i = 0; i < TOUR_STEPS && !(holder(anna) instanceof GameMenu); i++) {
            List<String> step = TestServer.messages(anna);
            told.addAll(step);
            if (told(step, "another plugin refused it")) {
                assertNotEquals(builds.get(ben), anna.getLocation(), "not taken there");
                assertSheltered(anna, true, "while Ben's build is on show");
            }
            later(viewMs());
            tick();
        }
        assertTrue(told(told, "You could not be taken to the build by Ben - another plugin refused it"), told.toString());
        assertInstanceOf(GameMenu.class, holder(anna), "the vote follows the tour");
        for (PlayerMock p : everyone) assertHome(p, "back after the tour");
    }

    /** on the death screen: Paper refuses to move a dead player, which is no other plugin's doing */
    @Test
    void aDeadPlayerIsNotTakenAlong() {
        tour();
        anna.setHealth(0);
        TestServer.messages(anna);
        later(viewMs());
        tick();
        assertTrue(builds.containsValue(host.getLocation()), "the tour moved on: " + host.getLocation());
        assertFalse(told(TestServer.messages(anna), "another plugin refused it"));
    }
}
