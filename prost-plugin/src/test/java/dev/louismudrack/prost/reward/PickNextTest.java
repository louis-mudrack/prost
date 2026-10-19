package dev.louismudrack.prost.reward;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.menu.ChoiceMenu;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** "Pick the next challenge" with a Solo or Duel card: the winner also names who plays, on the clock of every other choice. */
class PickNextTest {
    /** one entry of that kind whose only reward is picking the next challenge */
    private static String catalogue(String kind) {
        return "pick: {name: {en: \"Bunny\"}, text: {en: \"Jump {amount} times\"}, tracker: stat, "
                + "stat: JUMP, amount: 200, seconds: 240, sips: 2, kinds: [" + kind + "], rewards: [next]}\n";
    }

    /** ChoiceMenu puts a single option in the middle, two options left and right of it */
    private static final int SINGLE_OPTION = 13, FIRST_OF_TWO = 12;
    private static final int CLOCK_SLOT = 4;

    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host;
    private Round round;
    private ChallengeDef card;

    @BeforeEach
    void mock() { server = TestServer.mock(); }

    /** Prost with a one-card catalogue, and the host's round */
    private void start(String kind) {
        plugin = TestServer.start(server, folder -> TestServer.catalogue(folder, catalogue(kind)));
        host = server.addPlayer("Host");
        host.performCommand("prost start");
        round = plugin.rounds().of(host.getUniqueId());
        card = plugin.challenges().get(0);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private InventoryHolder holder() {
        Inventory top = host.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder(false);
    }

    /**
     * MockBukkit has no top inventory once every menu is closed (a real client always has one), and the clocks of
     * closed menus look at it until their next tick: the host opens the main menu, which has no clock
     */
    private void openMainMenu() { host.performCommand("prost"); }

    private void click(int slot) {
        new PlayerSimulation(host).simulateInventoryClick(host.getOpenInventory(), slot);
        server.getScheduler().performTicks(1);   // a menu acts one tick after the click
    }

    /** the winner picked the card: the "who plays" picker is open */
    private ChoiceMenu whoPlays() {
        plugin.rewards().grant(round, host.getUniqueId(), card, false, List.of());
        assertInstanceOf(ChoiceMenu.class, holder(), "the card picker");
        click(SINGLE_OPTION);
        return assertInstanceOf(ChoiceMenu.class, holder(), "the who-plays picker");
    }

    private long rewardTicks() { return plugin.cfg().rewardSeconds() * 20L; }

    @Test
    void theWhoPlaysPickerRunsOutAndTheDrawPicks() {
        start("solo");
        ChoiceMenu who = whoPlays();
        assertEquals(Material.CLOCK, who.getInventory().getItem(CLOCK_SLOT).getType(), "the countdown clock");
        TestServer.messages(host);
        server.getScheduler().performTicks(rewardTicks());
        assertNotNull(round.queued, "the card still comes next");
        assertEquals(card.id(), round.queued.id());
        assertEquals(List.of(), round.queued.players(), "nobody named: the draw picks, as for any Solo");
        List<String> told = TestServer.messages(host);
        assertTrue(told.stream().anyMatch(m -> m.contains("Time's up - the game picks who plays.")), told.toString());
        assertFalse(holder() instanceof ChoiceMenu, "the picker closed");
    }

    @Test
    void aPickInTimeStandsAndTheClockStops() {
        start("solo");
        whoPlays();
        click(SINGLE_OPTION);
        assertEquals(List.of(host.getUniqueId()), round.queued.players());
        openMainMenu();
        TestServer.messages(host);
        server.getScheduler().performTicks(rewardTicks());
        assertEquals(List.of(host.getUniqueId()), round.queued.players());
        assertTrue(TestServer.messages(host).isEmpty(), "no time-up message after the pick");
    }

    /** a Duel names two: both picks share the one clock, and a time-out after the first name drops it - the draw picks both */
    @Test
    void aDuelPickRunsOnOneClockAndATimeOutDropsTheFirstName() {
        start("duel");
        PlayerMock guest = server.addPlayer("Guest");
        guest.performCommand("prost join " + round.code);
        ChoiceMenu first = whoPlays();
        click(FIRST_OF_TWO);   // one tick of the clock
        ChoiceMenu second = assertInstanceOf(ChoiceMenu.class, holder(), "the second pick");
        assertNotSame(first, second);
        assertEquals(Material.CLOCK, second.getInventory().getItem(CLOCK_SLOT).getType());
        TestServer.messages(host);
        server.getScheduler().performTicks(rewardTicks() - 1);   // a clock started for the second pick would run one tick longer
        assertNotNull(round.queued, "the card still comes next");
        assertEquals(List.of(), round.queued.players(), "the first name is dropped: the draw picks both");
        List<String> told = TestServer.messages(host);
        assertTrue(told.stream().anyMatch(m -> m.contains("Time's up - the game picks who plays.")), told.toString());
    }
}
