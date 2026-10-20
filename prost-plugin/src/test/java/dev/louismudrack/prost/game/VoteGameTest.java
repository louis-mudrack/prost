package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.ProstApi;
import dev.louismudrack.prost.api.event.ChallengeEndEvent;
import dev.louismudrack.prost.menu.GameMenu;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The build contest in a round of four, on a clock the test moves by hand; clicks act one tick later, as on a server. */
class VoteGameTest {
    private static final String STATUE = "vote_statue", PORTRAIT = "vote_portrait";
    private static final String MARKED = "Your build is marked", MODEL = "Your model: ", BUILD_BY = "Build by ";
    /** more steps than a tour of four builds can take */
    private static final int TOUR_STEPS = 10;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final AtomicBoolean gateOpen = new AtomicBoolean(true);
    private final List<ChallengeEndEvent> ended = new ArrayList<>();
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host, anna, ben, cleo;
    private List<PlayerMock> everyone;
    private Round round;

    public class Recorder implements Listener {
        @EventHandler public void on(ChallengeEndEvent e) { ended.add(e); }
    }

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        plugin.clock(() -> now.get());
        server.getPluginManager().registerEvents(new Recorder(), plugin);
        host = server.addPlayer("Host");
        anna = server.addPlayer("Anna");
        ben = server.addPlayer("Ben");
        cleo = server.addPlayer("Cleo");
        everyone = List.of(host, anna, ben, cleo);
        host.performCommand("prost start");
        round = plugin.rounds().of(host.getUniqueId());
        for (PlayerMock p : List.of(anna, ben, cleo)) p.performCommand("prost join " + round.code);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private ChallengeRun force(String id) {
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + round.code + " " + id + " vote");
        ChallengeRun run = plugin.director().running(round);
        assertNotNull(run, id + " runs as vote");
        tick();   // the first poll tells the buttons
        return run;
    }

    private void later(long ms) { now.addAndGet(ms); }

    private void tick() { plugin.director().tick(); }

    private boolean running() { return plugin.director().running(round) != null; }

    /** the end of the building time, then build after build until the vote menus open: what the host was told on the way */
    private List<String> toVote(ChallengeRun run) {
        TestServer.messages(host);
        later(run.def.seconds() * 1000L);
        tick();
        List<String> told = new ArrayList<>(TestServer.messages(host));
        for (int i = 0; i < TOUR_STEPS && !(holder(host) instanceof GameMenu); i++) {
            later(plugin.cfg().games().voteViewSeconds() * 1000L);
            tick();
            told.addAll(TestServer.messages(host));
        }
        assertInstanceOf(GameMenu.class, holder(host), "the vote menu after the tour: " + told);
        return told;
    }

    private void endVote() {
        later(plugin.cfg().games().voteVoteSeconds() * 1000L);
        tick();
        assertFalse(running(), "the vote ends at its deadline");
    }

    private ChallengeEndEvent end() {
        assertEquals(1, ended.size(), "one end");
        assertTrue(ended.get(0).resolved(), "resolved, not cancelled");
        return ended.get(0);
    }

    private static UUID id(PlayerMock p) { return p.getUniqueId(); }

    private static boolean told(List<String> lines, String part) { return lines.stream().anyMatch(m -> m.contains(part)); }

    private static String barName(PlayerMock p) {
        List<BossBar> bars = new ArrayList<>(p.getBossBars());
        assertEquals(1, bars.size(), p.getName() + "'s bars");
        return PlainTextComponentSerializer.plainText().serialize(bars.get(0).name());
    }

    private static InventoryHolder holder(PlayerMock p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder(false);
    }

    private static ItemStack item(PlayerMock p, int slot) { return p.getOpenInventory().getTopInventory().getItem(slot); }

    /** by name: MockBukkit's skull keeps the owner's name */
    private static String headOwner(ItemStack it) {
        if (it == null || it.getType() != Material.PLAYER_HEAD) return null;
        return ((SkullMeta) it.getItemMeta()).getOwningPlayer().getName();
    }

    private static List<String> heads(PlayerMock viewer) {
        List<String> out = new ArrayList<>();
        Inventory top = viewer.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < top.getSize(); slot++) {
            String owner = headOwner(top.getItem(slot));
            if (owner != null) out.add(owner);
        }
        return out;
    }

    private static int slotOf(PlayerMock viewer, String name) {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < top.getSize(); slot++) if (name.equals(headOwner(top.getItem(slot)))) return slot;
        throw new AssertionError(name + " has no head in " + viewer.getName() + "'s menu");
    }

    private static List<String> lore(ItemStack it) {
        List<Component> lore = it.getItemMeta().lore();
        return lore == null ? List.of() : lore.stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
    }

    private void click(PlayerMock p, int slot) {
        new PlayerSimulation(p).simulateInventoryClick(p.getOpenInventory(), slot);
        server.getScheduler().performTicks(1);
    }

    private void vote(PlayerMock voter, PlayerMock builder) { click(voter, slotOf(voter, builder.getName())); }

    private Location at(int x, int y, int z) { return new Location(server.getWorld("world"), x, y, z); }

    @Test
    void hereMarksTheSpotOnlyWhileBuilding() {
        anna.performCommand("prost here");
        assertTrue(told(TestServer.messages(anna), "There is no build to mark right now"), "no contest running");
        ChallengeRun run = force(STATUE);
        assertTrue(told(TestServer.messages(anna), "[Mark my build here]"), "the chat button");
        anna.teleport(at(3, 64, 7));
        anna.performCommand("prost here");
        assertTrue(told(TestServer.messages(anna), MARKED + ": world 3 64 7"));
        tick();
        assertTrue(barName(anna).contains("Marked at 3 64 7"), barName(anna));
        assertTrue(barName(host).contains("/prost here marks the spot"), barName(host));
        later(run.def.seconds() * 1000L);
        tick();
        assertTrue(barName(anna).contains("Now: the build by"), "the tour: " + barName(anna));
        TestServer.messages(anna);
        anna.performCommand("prost here");
        List<String> told = TestServer.messages(anna);
        assertTrue(told(told, "There is no build to mark right now"), "the tour has begun: " + told);
        assertFalse(told(told, MARKED));
    }

    @Test
    void theGermanAliasHierWorks() {
        cleo.performCommand("prost language de");
        force(STATUE);
        TestServer.messages(cleo);
        cleo.performCommand("prost hier");
        List<String> told = TestServer.messages(cleo);
        assertTrue(told(told, "Dein Bau ist markiert"), told.toString());
    }

    /** config.yml excluded-areas: a box around (100, 64, 100) in world */
    private void excludeAround100() throws Exception {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        c.set("excluded-areas", List.of(Map.of("world", "world", "min", List.of(90, 0, 90), "max", List.of(110, 200, 110))));
        c.save(file);
        plugin.reloadData();
    }

    /** the ding of a build's turn (Sounds.ding) */
    private static long dings(PlayerMock p) { return p.getHeardSounds().stream().filter(s -> s.getSound().contains("note_block.pling")).count(); }

    /** "Build by Anna: ..." -> Anna */
    private static List<String> shownBuilders(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            int at = line.indexOf(BUILD_BY);
            if (at >= 0) out.add(line.substring(at + BUILD_BY.length(), line.indexOf(':', at)));
        }
        return out;
    }

    @Test
    void aSpotInAnExcludedAreaIsRefused() throws Exception {
        excludeAround100();
        anna.teleport(at(100, 64, 100));
        ChallengeRun run = force(STATUE);
        TestServer.messages(anna);
        anna.performCommand("prost here");
        List<String> told = TestServer.messages(anna);
        assertTrue(told(told, "excluded"), told.toString());
        assertFalse(told(told, MARKED));
        List<String> tour = toVote(run);
        assertTrue(told(tour, "Build by Anna: can't be visited"), "still in the area when the time ran out: " + tour);
        assertEquals(Set.of("Anna", "Ben", "Cleo"), new HashSet<>(heads(host)), "a build that cannot be visited stays votable");
    }

    /** a Nether world behind a gate the test opens and closes, as AniOps' /ani survival nether open|close does */
    private World netherBehindAGate() {
        WorldMock nether = server.addSimpleWorld("world_nether");
        nether.setEnvironment(World.Environment.NETHER);
        server.getServicesManager().load(ProstApi.class).registerDimensionGate(MockBukkit.createMockPlugin("Network"), env -> gateOpen.get());
        return nether;
    }

    @Test
    void hereInAClosedDimensionIsRefused() {
        World nether = netherBehindAGate();
        gateOpen.set(false);
        force(STATUE);
        anna.teleport(new Location(nether, 4, 70, 4));
        TestServer.messages(anna);
        anna.performCommand("prost here");
        List<String> told = TestServer.messages(anna);
        assertTrue(told(told, "This dimension is closed right now - not marked."), told.toString());
        assertFalse(told(told, MARKED));
        gateOpen.set(true);
        anna.performCommand("prost here");
        assertTrue(told(TestServer.messages(anna), MARKED + ": world_nether 4 70 4"), "open again: marked");
    }

    @Test
    void noSpotIsTakenInAClosedDimensionWhenTheTimeIsUp() {
        World nether = netherBehindAGate();
        ChallengeRun run = force(STATUE);
        ben.teleport(new Location(nether, 4, 70, 4));
        gateOpen.set(false);
        List<String> tour = toVote(run);
        assertTrue(told(tour, "Build by Ben: can't be visited (no spot)"), tour.toString());
        assertTrue(heads(host).contains("Ben"), "still votable");
    }

    @Test
    void withoutAMarkTheSpotIsWhereTheBuilderStands() {
        ChallengeRun run = force(STATUE);
        ben.teleport(at(12, 70, -5));
        List<String> tour = toVote(run);
        assertTrue(told(tour, "Build by Ben: world 12 70 -5"), tour.toString());
    }

    @Test
    void portraitTellsEachBuilderSomeoneElse() {
        for (PlayerMock p : everyone) TestServer.messages(p);
        force(PORTRAIT);
        Set<String> names = Set.of("Host", "Anna", "Ben", "Cleo"), subjects = new HashSet<>();
        for (PlayerMock p : everyone) {
            List<String> lines = TestServer.messages(p).stream().filter(m -> m.contains(MODEL)).toList();
            assertEquals(1, lines.size(), p.getName() + " is told one model, privately: " + lines);
            String rest = lines.get(0).substring(lines.get(0).indexOf(MODEL) + MODEL.length());
            String subject = rest.substring(0, rest.indexOf(' '));
            assertTrue(names.contains(subject), subject);
            assertNotEquals(p.getName(), subject, "nobody portrays themselves");
            subjects.add(subject);
        }
        assertEquals(names, subjects, "everybody is somebody's model");
    }

    @Test
    void theVoteMenuShowsOnlyTheOthersAndAVoteCanChange() {
        ChallengeRun run = force(STATUE);
        toVote(run);
        assertEquals(3, heads(host).size(), "three heads for four players");
        assertEquals(Set.of("Anna", "Ben", "Cleo"), new HashSet<>(heads(host)), "not the own head");
        assertEquals(Material.CLOCK, item(host, 4).getType(), "the vote's clock");
        vote(host, ben);
        assertTrue(lore(item(host, slotOf(host, "Ben"))).contains("Your vote"));
        vote(host, cleo);
        assertTrue(lore(item(host, slotOf(host, "Cleo"))).contains("Your vote"), "the vote moved to Cleo");
        assertFalse(lore(item(host, slotOf(host, "Ben"))).contains("Your vote"), "and away from Ben");
        tick();
        assertTrue(barName(host).contains("Voted for Cleo"), barName(host));
        TestServer.messages(host);
        endVote();
        String result = TestServer.messages(host).stream().filter(m -> m.contains("Votes:")).findFirst().orElseThrow();
        assertTrue(result.contains("Cleo 1") && result.contains("Ben 0"), "one vote, for Cleo: " + result);
        for (PlayerMock p : everyone) assertFalse(holder(p) instanceof GameMenu, "the menus close at the end");
    }

    @Test
    void aClosedVoteMenuOpensAgainWithProstPlay() {
        ChallengeRun run = force(STATUE);
        TestServer.messages(anna);
        anna.performCommand("prost play");
        assertTrue(told(TestServer.messages(anna), "no game menu"), "nothing to open while building");
        toVote(run);
        assertTrue(told(TestServer.messages(anna), "[Open the game]"), "the chat button with the vote");
        anna.closeInventory();
        assertFalse(holder(anna) instanceof GameMenu);
        anna.performCommand("prost play");
        assertInstanceOf(GameMenu.class, holder(anna), "open again");
        assertEquals(Set.of("Host", "Ben", "Cleo"), new HashSet<>(heads(anna)));
    }

    @Test
    void fewestVotesDrinkAndAbstainersDrinkOneMore() {
        ChallengeRun run = force(STATUE);
        toVote(run);
        vote(host, anna);
        vote(anna, host);
        vote(ben, anna);
        TestServer.messages(host);
        endVote();
        int sips = run.def.sips();
        assertEquals(Map.of(id(ben), sips, id(cleo), sips + 1), end().drinks(), "Ben and Cleo got no vote, Cleo did not vote");
        assertEquals(List.of(id(anna)), end().winners());
        String result = TestServer.messages(host).stream().filter(m -> m.contains("Votes:")).findFirst().orElseThrow();
        assertTrue(result.contains("Anna 2 · Host 1"), result);
    }

    @Test
    void tooFewPlayersAtTheEndOfBuildingCancels() {
        ChallengeRun run = force(STATUE);
        ben.performCommand("prost leave");
        cleo.performCommand("prost leave");
        TestServer.messages(host);
        later(run.def.seconds() * 1000L);
        tick();
        assertFalse(running());
        assertEquals(1, ended.size());
        assertFalse(ended.get(0).resolved(), "cancelled");
        assertTrue(ended.get(0).drinks().isEmpty(), "nobody drinks");
        assertTrue(told(TestServer.messages(host), "Fewer than three players"));
    }

    @Test
    void aBuilderWhoDropsOutIsNoLongerVotable() {
        ChallengeRun run = force(STATUE);
        toVote(run);
        vote(host, ben);
        TestServer.messages(host);
        ben.performCommand("prost pause");
        tick();
        assertEquals(Set.of("Anna", "Cleo"), new HashSet<>(heads(host)));
        assertTrue(told(TestServer.messages(host), "Ben is out - your vote is gone"), "the voter hears it");
        assertTrue(barName(host).contains("Vote in the menu"), "no vote any more: " + barName(host));
        vote(anna, host);
        vote(cleo, host);
        endVote();
        assertFalse(end().drinks().containsKey(id(ben)), "out without drinking");
        assertEquals(Map.of(id(anna), run.def.sips(), id(cleo), run.def.sips(), id(host), 1), end().drinks(),
                "Anna and Cleo have no vote, the host's vote for Ben is gone");
    }

    /** the member is gone from the round before the run drops them: the contest still knows their name */
    @Test
    void aBuilderWhoLeavesOrIsKickedKeepsTheirName() {
        ChallengeRun run = force(STATUE);
        toVote(run);
        vote(host, ben);
        vote(anna, cleo);
        TestServer.messages(host);
        TestServer.messages(anna);
        ben.performCommand("prost leave");
        host.performCommand("prost kick Cleo");
        tick();
        List<String> toHost = TestServer.messages(host), toAnna = TestServer.messages(anna);
        assertTrue(told(toHost, "Ben is out - your vote is gone"), toHost.toString());
        assertTrue(told(toAnna, "Cleo is out - your vote is gone"), toAnna.toString());
        assertFalse(told(toHost, "? is out") || told(toAnna, "? is out"));
        assertEquals(Set.of("Anna"), new HashSet<>(heads(host)));
    }

    /** a build without a spot is only named in chat: no title, no ding, no viewing time */
    @Test
    void aBuildWithoutASpotIsOnlyNamedInChat() throws Exception {
        excludeAround100();
        anna.teleport(at(100, 64, 100));
        ChallengeRun run = force(STATUE);
        long before = dings(host);
        List<String> tour = toVote(run);
        assertTrue(told(tour, "Build by Anna: can't be visited"), tour.toString());
        assertEquals(3, dings(host) - before, "one ding for each of the three builds that can be visited");
        long viewMs = plugin.cfg().games().voteViewSeconds() * 1000L;
        assertEquals(run.def.seconds() * 1000L + 3 * viewMs, now.get() - 1_000_000L, "the vote opens after three viewing times, not four");
    }

    @Test
    void aSpotInAWorldThatWasUnloadedCountsAsMissing() {
        Location home = ben.getLocation();
        server.addSimpleWorld("other");
        ChallengeRun run = force(STATUE);
        ben.teleport(new Location(server.getWorld("other"), 5, 64, 5));
        ben.performCommand("prost here");
        ben.teleport(home);
        assertTrue(server.unloadWorld("other", false), "the world unloads");
        tick();
        assertTrue(barName(ben).contains("/prost here marks the spot"), "no spot any more: " + barName(ben));
        List<String> tour = toVote(run);
        assertTrue(told(tour, "Build by Ben: can't be visited"), tour.toString());
        assertTrue(heads(host).contains("Ben"), "still votable");
    }

    @Test
    void theTourMovesOnWhenTheBuilderOnShowDropsOut() {
        ChallengeRun run = force(STATUE);
        for (PlayerMock p : everyone) TestServer.messages(p);
        later(run.def.seconds() * 1000L);
        tick();
        List<String> first = shownBuilders(TestServer.messages(host));
        assertEquals(1, first.size(), "one build on show");
        PlayerMock leaving = (PlayerMock) server.getPlayerExact(first.get(0));
        PlayerMock reader = everyone.stream().filter(p -> p != leaving).findFirst().orElseThrow();
        TestServer.messages(reader);
        leaving.performCommand("prost leave");
        tick();
        List<String> next = shownBuilders(TestServer.messages(reader));
        assertEquals(1, next.size(), "the next build at once, no time passed: " + next);
        assertNotEquals(leaving.getName(), next.get(0));
    }

    @Test
    void aVoteAfterTheDeadlineDoesNotCount() {
        ChallengeRun run = force(STATUE);
        toVote(run);
        vote(host, anna);
        vote(anna, host);
        vote(ben, anna);
        later(plugin.cfg().games().voteVoteSeconds() * 1000L);
        vote(cleo, ben);
        tick();
        assertFalse(running());
        int sips = run.def.sips();
        assertEquals(Map.of(id(ben), sips, id(cleo), sips + 1), end().drinks(), "Cleo's vote came after the clock: " + end().drinks());
    }

    @Test
    void hereOutsideTheGameWorldsIsRefused() throws Exception {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration c = YamlConfiguration.loadConfiguration(file);
        c.set("worlds", List.of("world"));
        c.save(file);
        plugin.reloadData();
        force(STATUE);
        anna.teleport(new Location(server.addSimpleWorld("other"), 0, 64, 0));
        TestServer.messages(anna);
        anna.performCommand("prost here");
        List<String> told = TestServer.messages(anna);
        assertTrue(told(told, "Builds only count in the worlds Prost plays in"), told.toString());
        assertFalse(told(told, MARKED));
    }

    @Test
    void aWatchersHereIsRefused() {
        force(STATUE);
        PlayerMock dora = server.addPlayer("Dora");
        dora.performCommand("prost join " + round.code);
        TestServer.messages(dora);
        dora.performCommand("prost here");
        assertTrue(told(TestServer.messages(dora), "There is no build to mark right now"));
    }

    @Test
    void fewerThanTwoLeftAfterTheBuildingCancels() {
        ChallengeRun run = force(STATUE);
        toVote(run);
        anna.performCommand("prost leave");
        ben.performCommand("prost leave");
        cleo.performCommand("prost leave");
        tick();
        assertFalse(running());
        assertEquals(1, ended.size());
        assertFalse(ended.get(0).resolved(), "cancelled");
        assertTrue(ended.get(0).drinks().isEmpty(), "nobody drinks");
    }

    @Test
    void aBuilderWhoDropsDuringTheTourIsNotShownAndNotVotable() {
        ChallengeRun run = force(STATUE);
        for (PlayerMock p : everyone) TestServer.messages(p);
        later(run.def.seconds() * 1000L);
        tick();
        List<String> first = shownBuilders(TestServer.messages(host));
        PlayerMock leaving = (PlayerMock) server.getPlayerExact(first.get(0));
        PlayerMock reader = everyone.stream().filter(p -> p != leaving).findFirst().orElseThrow();
        leaving.performCommand("prost leave");
        List<String> shown = new ArrayList<>(first);
        TestServer.messages(reader);
        for (int i = 0; i < TOUR_STEPS && !(holder(reader) instanceof GameMenu); i++) {
            tick();
            later(plugin.cfg().games().voteViewSeconds() * 1000L);
            tick();
            shown.addAll(shownBuilders(TestServer.messages(reader)));
        }
        assertEquals(1, shown.stream().filter(leaving.getName()::equals).count(), "shown once, then never again: " + shown);
        assertFalse(heads(reader).contains(leaving.getName()), "not votable");
    }

    @Test
    void stoppingTheGameClosesItsMenus() {
        toVote(force(STATUE));
        plugin.director().shutdown();
        for (PlayerMock p : everyone) assertFalse(holder(p) instanceof GameMenu, p.getName() + "'s menu is closed");
    }
}
