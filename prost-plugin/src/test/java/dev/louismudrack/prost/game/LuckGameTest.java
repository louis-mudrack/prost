package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.event.ChallengeEndEvent;
import dev.louismudrack.prost.menu.ChoiceMenu;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.HandoutMenu;
import org.bukkit.Material;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The luck games in a round of three, on a clock the test moves by hand; clicks act one tick later, as on a server. */
class LuckGameTest {
    private static final int CLOCK_SLOT = 4;
    /** the wheel's spin button; three chests or three boxes sit at 11, 13 and 15 */
    private static final int MIDDLE = 13, LEFT = 11;
    /** the game's first draw is the prize behind the opened chest: seed 2 draws index 1 of (drink, joker, hand out) */
    private static final long JOKER_SEED = 2L;
    private static final Pattern SPIN = Pattern.compile("(Host|Anna|Ben) (\\d+)");

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<ChallengeEndEvent> ended = new ArrayList<>();
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host, anna, ben;
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
        host.performCommand("prost start");
        round = plugin.rounds().of(host.getUniqueId());
        anna.performCommand("prost join " + round.code);
        ben.performCommand("prost join " + round.code);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private ChallengeRun force(String id, String kind) {
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + round.code + " " + id + " " + kind);
        ChallengeRun run = plugin.director().running(round);
        assertNotNull(run, id + " runs as " + kind);
        return run;
    }

    private void later(long ms) { now.addAndGet(ms); }

    private void tick() { plugin.director().tick(); }

    private boolean running() { return plugin.director().running(round) != null; }

    private long revealMs() { return plugin.cfg().games().luckRevealSeconds() * 1000L; }

    private ChallengeEndEvent end() {
        assertEquals(1, ended.size(), "one end");
        assertTrue(ended.get(0).resolved(), "resolved, not cancelled");
        return ended.get(0);
    }

    private static UUID id(PlayerMock p) { return p.getUniqueId(); }

    private PlayerMock player(UUID id) { return (PlayerMock) server.getPlayer(id); }

    private static InventoryHolder holder(PlayerMock p) {
        Inventory top = p.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder(false);
    }

    private static ItemStack item(PlayerMock p, int slot) { return p.getOpenInventory().getTopInventory().getItem(slot); }

    private void click(PlayerMock p, int slot) {
        new PlayerSimulation(p).simulateInventoryClick(p.getOpenInventory(), slot);
        server.getScheduler().performTicks(1);
    }

    /** by name: MockBukkit's skull keeps the owner's name and hands back an offline player made from it */
    private static String headOwner(ItemStack it) {
        assertEquals(Material.PLAYER_HEAD, it.getType());
        return ((SkullMeta) it.getItemMeta()).getOwningPlayer().getName();
    }

    @Test
    void everySpinnerGetsAMenuWithTheClock() {
        force("wheel", "luck");
        for (PlayerMock p : List.of(host, anna, ben)) {
            GameMenu menu = assertInstanceOf(GameMenu.class, holder(p), p.getName() + "'s menu");
            assertEquals(Material.CLOCK, menu.getInventory().getItem(CLOCK_SLOT).getType(), p.getName() + "'s clock");
        }
    }

    @Test
    void theLowestSpinDrinksAfterTheReveal() {
        force("wheel", "luck");
        for (PlayerMock p : List.of(host, anna, ben)) click(p, MIDDLE);
        tick();
        assertEquals(Material.PAPER, item(host, MIDDLE).getType(), "the reveal shows a number");
        assertNotEquals(Material.CLOCK, item(host, CLOCK_SLOT).getType(), "decided: no clock any more");
        later(revealMs() - 1);
        tick();
        assertTrue(running(), "everybody spun: the reveal runs before the end");
        TestServer.messages(host);
        later(1);
        tick();
        assertFalse(running());
        Map<UUID, Integer> drinks = end().drinks();
        assertFalse(drinks.isEmpty(), "somebody always has the lowest number");
        for (int sips : drinks.values()) assertEquals(2, sips);
        Map<UUID, Integer> rolls = spins(TestServer.messages(host));
        int lowest = rolls.values().stream().mapToInt(n -> n).min().orElseThrow();
        for (PlayerMock p : List.of(host, anna, ben))
            assertEquals(rolls.get(id(p)) == lowest, drinks.containsKey(id(p)), p.getName() + " spun " + rolls.get(id(p)) + ", the lowest was " + lowest);
        for (PlayerMock p : List.of(host, anna, ben)) assertFalse(holder(p) instanceof GameMenu, "the menus close at the end");
    }

    /** "Wheel of fortune » Spins: Host 7 · Anna 3 · Ben 3" */
    private Map<UUID, Integer> spins(List<String> told) {
        String line = told.stream().filter(m -> m.contains("Spins:")).findFirst().orElseThrow(() -> new AssertionError(told.toString()));
        Map<UUID, Integer> out = new HashMap<>();
        Matcher m = SPIN.matcher(line);
        while (m.find()) out.put(id((PlayerMock) server.getPlayerExact(m.group(1))), Integer.parseInt(m.group(2)));
        assertEquals(3, out.size(), line);
        return out;
    }

    @Test
    void aMissingChoiceIsMadeAtTheDeadline() {
        ChallengeRun run = force("wheel", "luck");
        later(run.def.seconds() * 1000L);
        tick();
        assertTrue(running(), "the missing spins are made, then revealed");
        later(revealMs());
        tick();
        assertFalse(running());
        assertFalse(end().drinks().isEmpty(), "everybody got a number, the lowest drinks");
    }

    /** nobody decides: every game, in every kind it ships with, still ends with its own result line */
    @Test
    void everyGameEndsAtItsDeadline() {
        String[][] games = {{"wheel", "luck"}, {"wheel", "solo"}, {"coin_flip", "duel"}, {"three_chests", "solo"}, {"dice", "solo"},
                {"high_low", "duel"}, {"high_low", "solo"}, {"mystery_box", "luck"}};
        for (String[] g : games) {
            ended.clear();
            ChallengeRun run = force(g[0], g[1]);
            later(run.def.seconds() * 1000L);
            tick();
            later(revealMs());
            TestServer.messages(host);
            tick();
            assertFalse(running(), g[0] + " as " + g[1]);
            end();
            List<String> told = TestServer.messages(host);
            assertTrue(told.stream().anyMatch(m -> m.startsWith("Prost » " + run.def.name("en") + " » ")), g[0] + ": " + told);
            assertTrue(told.stream().noneMatch(m -> m.contains("Made it:") || m.contains("Nobody made it")), g[0] + " words its own result: " + told);
            for (PlayerMock p : List.of(host, anna, ben)) p.closeInventory();   // a reward menu of this game
        }
    }

    @Test
    void aClosedMenuOpensAgainWithProstPlay() {
        ChallengeRun run = force("wheel", "luck");
        TestServer.messages(anna);
        tick();
        assertTrue(TestServer.messages(anna).stream().anyMatch(m -> m.contains("[Open the game]")), "the chat button");
        anna.closeInventory();
        assertFalse(holder(anna) instanceof GameMenu);
        anna.performCommand("prost play");
        assertInstanceOf(GameMenu.class, holder(anna), "open again");
        later(run.def.seconds() * 1000L);
        tick();
        later(revealMs());
        tick();
        assertFalse(running());
        TestServer.messages(anna);
        anna.performCommand("prost play");
        assertTrue(TestServer.messages(anna).stream().anyMatch(m -> m.contains("no game menu")), "nothing to open");
    }

    @Test
    void aChestJokerIsGivenWithoutAChoiceMenu() {
        plugin.random(new Random(JOKER_SEED));
        assertEquals(1, new Random(JOKER_SEED).nextInt(3), "the seed hides the joker in the opened chest");
        UUID playing = force("three_chests", "solo").participants().get(0);
        PlayerMock player = player(playing);
        int before = round.member(playing).jokers;
        click(player, LEFT);
        tick();
        later(revealMs());
        tick();
        assertFalse(running());
        assertEquals(List.of(playing), end().winners());
        assertTrue(end().drinks().isEmpty());
        assertEquals(before + 1, round.member(playing).jokers, "the chest's joker");
        InventoryHolder open = holder(player);
        assertFalse(open instanceof ChoiceMenu || open instanceof HandoutMenu, "no reward menu: the chest decided it");
    }

    @Test
    void boxesShowTheTakersHeadAndATakenBoxCannotBePicked() {
        force("mystery_box", "luck");
        click(host, LEFT);
        assertEquals("Host", headOwner(item(anna, LEFT)), "Anna sees the host's head on the taken box");
        click(anna, LEFT);
        assertEquals("Host", headOwner(item(anna, LEFT)), "still the host's box");
        assertNotEquals(Material.PLAYER_HEAD, item(anna, MIDDLE).getType(), "nobody has the middle box");
        click(anna, MIDDLE);
        assertEquals("Anna", headOwner(item(anna, MIDDLE)), "the refused click did not use up Anna's pick");
        assertEquals("Anna", headOwner(item(host, MIDDLE)), "the host sees it too");
    }

    @Test
    void theCoinCallerIsTheFirstDrawnPlayer() {
        List<UUID> duel = force("coin_flip", "duel").participants();
        assertInstanceOf(GameMenu.class, holder(player(duel.get(0))), "the caller decides");
        PlayerMock other = player(duel.get(1));
        assertFalse(holder(other) instanceof GameMenu, "the other one only waits");
        TestServer.messages(other);
        other.performCommand("prost play");
        assertTrue(TestServer.messages(other).stream().anyMatch(m -> m.contains("no game menu")), "nothing to open for the other one");
    }

    @Test
    void theCoinCallDecidesWhoDrinks() {
        List<UUID> duel = force("coin_flip", "duel").participants();
        click(player(duel.get(0)), LEFT);   // heads
        tick();
        later(revealMs());
        tick();
        Map<UUID, Integer> drinks = end().drinks();
        assertEquals(1, drinks.size(), "one of the two drinks");
        assertTrue(duel.containsAll(drinks.keySet()));
        assertEquals(List.of(duel.stream().filter(id -> !drinks.containsKey(id)).findFirst().orElseThrow()), end().winners());
    }

    @Test
    void whoDropsOutDuringTheRevealKeepsNothing() {
        force("wheel", "luck");
        for (PlayerMock p : List.of(host, anna, ben)) click(p, MIDDLE);
        tick();
        ben.performCommand("prost pause");
        later(revealMs());
        tick();
        assertFalse(end().drinks().containsKey(id(ben)), "out without drinking");
        assertFalse(end().winners().contains(id(ben)), "and without winning");
        assertEquals(2, end().drinks().size() + end().winners().size(), "the two who stayed are judged");
    }

    @Test
    void stakesAreWordedPerGame() {
        for (PlayerMock p : List.of(host, anna, ben)) TestServer.messages(p);
        force("wheel", "luck");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("The lowest number drinks 2 sips")), "the wheel's stakes");
    }

    @Test
    void stoppingTheGameClosesItsMenus() {
        force("mystery_box", "luck");
        plugin.director().shutdown();
        for (PlayerMock p : List.of(host, anna, ben)) assertFalse(holder(p) instanceof GameMenu, p.getName() + "'s menu is closed");
    }
}
