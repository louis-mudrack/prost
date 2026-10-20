package dev.louismudrack.prost.game;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.event.ChallengeEndEvent;
import dev.louismudrack.prost.menu.HandoutMenu;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Pig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** The reaction challenges in a round of three, on a clock the test moves by hand. */
class ReactionGameTest {
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

    private void force(String id, String kind) {
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + round.code + " " + id + " " + kind);
        assertNotNull(plugin.director().running(round), id + " runs as " + kind);
    }

    private void later(long ms) { now.addAndGet(ms); }

    private void tick() { plugin.director().tick(); }

    private boolean running() { return plugin.director().running(round) != null; }

    private ChallengeEndEvent end() {
        assertEquals(1, ended.size(), "one resolved end");
        assertTrue(ended.get(0).resolved());
        return ended.get(0);
    }

    private static UUID id(PlayerMock p) { return p.getUniqueId(); }

    private static void sneak(PlayerMock p, boolean down) { new PlayerSimulation(p).simulateSneak(down); }

    private static void move(PlayerMock p, Location to) { new PlayerSimulation(p).simulatePlayerMove(to); }

    /** on the chat thread, as on a real server */
    private void chat(PlayerMock p, Component message) throws Exception {
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        AsyncChatEvent e = new AsyncChatEvent(true, p, Set.of(), ChatRenderer.defaultRenderer(), message, message, SignedMessage.system(plain, message));
        server.getScheduler().executeAsyncEvent(e).get(5, TimeUnit.SECONDS);
    }

    @Test
    void crouchingInTimeLeavesTheSlowestToDrink() {
        force("react_crouch", "reaction");
        sneak(anna, true);
        later(100);
        sneak(ben, true);
        sneak(host, false);   // standing up is not crouching
        tick();
        assertTrue(running(), "the host has not crouched yet");
        later(15_000);
        tick();
        assertFalse(running());
        assertEquals(Map.of(id(host), 1), end().drinks());
        assertEquals(List.of(id(anna), id(ben)), end().winners());
    }

    @Test
    void everybodyCrouchingEndsEarlyAndTheLastDrinks() {
        force("react_crouch", "reaction");
        sneak(host, true);
        later(100);
        sneak(anna, true);
        later(100);
        sneak(ben, true);
        tick();
        assertFalse(running(), "everybody is done: no waiting for the clock");
        assertEquals(Map.of(id(ben), 1), end().drinks());
        assertEquals(List.of(id(host), id(anna)), end().winners());
    }

    @Test
    void whoDropsOutMidReactionIsNotWaitedForNorJudged() {
        force("react_crouch", "reaction");
        sneak(host, true);
        ben.performCommand("prost pause");
        tick();
        assertTrue(running(), "Anna has not crouched yet");
        later(100);
        sneak(anna, true);
        tick();
        assertFalse(running(), "everybody still playing is done");
        assertEquals(Map.of(id(anna), 1), end().drinks());
        assertEquals(List.of(id(host)), end().winners());
    }

    @Test
    void theOneLeftWhoIsDoneIsNobodysLast() {
        force("react_crouch", "reaction");
        anna.performCommand("prost pause");
        ben.performCommand("prost pause");
        tick();
        sneak(host, true);
        tick();
        assertFalse(running());
        assertTrue(end().drinks().isEmpty());
        assertEquals(List.of(id(host)), end().winners());
    }

    @Test
    void theChatWordCountsAsPlainTextOnly() throws Exception {
        force("react_chat", "reaction");
        for (PlayerMock p : List.of(host, anna, ben)) TestServer.messages(p);
        chat(anna, Component.text("prost ", NamedTextColor.RED));   // "<red>prost " as a chat plugin that colours chat hands it on
        chat(ben, Component.text("Prosit"));
        later(30_000);
        tick();
        assertEquals(Map.of(id(host), 1, id(ben), 1), end().drinks());
        assertEquals(List.of(id(anna)), end().winners());
        List<Component> told = new ArrayList<>();
        for (PlayerMock p : List.of(host, anna, ben))
            for (Component c = p.nextComponentMessage(); c != null; c = p.nextComponentMessage()) told.add(c);
        assertTrue(told.stream().anyMatch(c -> PlainTextComponentSerializer.plainText().serialize(c).contains("Made it: Anna")), "the result was told");
        assertTrue(told.stream().noneMatch(c -> redWord(c, null)), "the typed word is never shown rendered");
    }

    /** a red text that contains the word, the colour inherited from the parents */
    private static boolean redWord(Component c, TextColor inherited) {
        TextColor color = c.color() != null ? c.color() : inherited;
        if (c instanceof TextComponent t && NamedTextColor.RED.equals(color) && t.content().toLowerCase(Locale.ROOT).contains("prost")) return true;
        return c.children().stream().anyMatch(child -> redWord(child, color));
    }

    /** the reaction kinds word their stakes: a freeze can be won by all, a solo freeze keeps the solo line */
    @Test
    void theStakesFollowTheReactionAndTheKind() {
        for (PlayerMock p : List.of(host, anna, ben)) TestServer.messages(p);
        force("react_freeze", "reaction");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Whoever moves: 1 sip · Nobody moves: hand out 1 sip")), "freeze");
        plugin.director().shutdown();
        force("react_crouch", "reaction");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Not in time or the last one: 1 sip")), "crouch");
        plugin.director().shutdown();
        force("react_freeze", "solo");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Miss it: 1 sip · Make it: hand out 1 sip")), "solo freeze");
    }

    @Test
    void freezeBreaksOnlyOnBodyMovementAfterTheGrace() {
        force("react_freeze", "reaction");
        tick();
        later(1_000);
        move(anna, anna.getLocation().clone().add(1, 0, 0));   // still in the grace, after a tick
        tick();
        later(1_500);
        tick();
        Location look = host.getLocation().clone();
        look.setYaw(look.getYaw() + 90);
        look.setPitch(-40);
        move(host, look);   // looking around is not moving
        move(ben, ben.getLocation().clone().add(0, 0, 1));
        tick();
        assertTrue(running(), "two still hold");
        later(10_000);
        tick();
        assertEquals(Map.of(id(ben), 1), end().drinks());
        assertEquals(List.of(id(host), id(anna)), end().winners());
    }

    @Test
    void soloInTimeIsRewardedAndNobodyDrinks() {
        force("react_water", "solo");
        UUID playing = plugin.director().running(round).participants().get(0);
        PlayerMock player = (PlayerMock) server.getPlayer(playing);
        player.setInWater(true);
        tick();
        assertFalse(running());
        assertTrue(end().drinks().isEmpty());
        assertEquals(List.of(playing), end().winners());
        assertInstanceOf(HandoutMenu.class, player.getOpenInventory().getTopInventory().getHolder(false), "the reward menu");
    }

    private static String barName(PlayerMock p) {
        List<BossBar> bars = new ArrayList<>(p.getBossBars());
        assertEquals(1, bars.size(), p.getName() + "'s bars");
        return PlainTextComponentSerializer.plainText().serialize(bars.get(0).name());
    }

    @Test
    void theBossBarShowsAReactionStatusNotAProgressCount() {
        anna.performCommand("prost pause");   // Anna watches
        force("react_crouch", "reaction");
        sneak(host, true);
        tick();
        String watching = barName(anna), hosts = barName(host), bens = barName(ben);
        assertTrue(watching.contains("Host done") && watching.contains("Ben waiting"), watching);
        assertTrue(hosts.contains("done"), hosts);
        assertTrue(bens.contains("Crouch"), "the player's own bar says what to do: " + bens);
        for (String bar : List.of(watching, hosts, bens)) assertFalse(bar.contains("0/1") || bar.contains("1/1"), bar);
    }

    /** host, Anna, Ben do it in turn, a tick apart: the run ends as soon as Ben has, and Ben was last */
    private void allInTurn(String id, Consumer<PlayerMock> action) {
        force(id, "reaction");
        int sips = plugin.director().running(round).def.sips();
        for (PlayerMock p : List.of(host, anna, ben)) {
            assertTrue(running(), id + " still runs before " + p.getName());
            action.accept(p);
            later(100);
            tick();
        }
        assertFalse(running(), id + " ends when everybody did it");
        assertEquals(Map.of(id(ben), sips), end().drinks());
    }

    @Test
    void lookingStraightUpCounts() {
        allInTurn("react_look_up", p -> {
            Location almost = p.getLocation().clone();
            almost.setPitch(-60);
            move(p, almost);
            assertTrue(running(), "-60 is not straight up");
            Location up = p.getLocation().clone();
            up.setPitch(-80);
            move(p, up);
        });
    }

    @Test
    void jumpingCounts() {
        allInTurn("react_jump", p -> server.getPluginManager().callEvent(new PlayerJumpEvent(p, p.getLocation(), p.getLocation().clone().add(0, 1, 0))));
    }

    @Test
    void holdingTheTargetCountsBySlotChangeOrInHand() {
        allInTurn("react_dirt", p -> {
            if (p == host) {
                p.getInventory().setItem(1, new ItemStack(Material.DIRT));
                server.getPluginManager().callEvent(new PlayerItemHeldEvent(p, 0, 1));
            } else {
                p.getInventory().setItemInMainHand(new ItemStack(Material.DIRT));
            }
        });
    }

    @Test
    void sittingOnAMountCounts() {
        allInTurn("react_sit", p -> p.getWorld().spawn(p.getLocation(), Pig.class).addPassenger(p));
    }

    @Test
    void eatingFoodCountsDrinkingMilkDoesNot() {
        allInTurn("react_eat", p -> {
            if (p == host) consume(ben, Material.MILK_BUCKET);   // would make Ben first if drinking counted
            consume(p, Material.BREAD);
        });
    }

    private void consume(PlayerMock p, Material what) {
        server.getPluginManager().callEvent(new PlayerItemConsumeEvent(p, new ItemStack(what), EquipmentSlot.HAND));
    }

    private Pig mount(PlayerMock p) {
        Pig pig = p.getWorld().spawn(p.getLocation(), Pig.class);
        pig.addPassenger(p);
        return pig;
    }

    /** the mount is the moment: three in turn between two polls are not a three-way tie */
    @Test
    void sittingCountsAtTheMomentOfTheMount() {
        force("react_sit", "reaction");
        for (PlayerMock p : List.of(host, anna, ben)) {
            mount(p);
            later(100);
        }
        tick();
        assertFalse(running());
        assertEquals(Map.of(id(ben), 2), end().drinks());
        assertEquals(List.of(id(host), id(anna)), end().winners());
    }

    @Test
    void whoAlreadySitsMustGetUpAndSitDownAgain() {
        Pig pig = mount(host);
        force("react_sit", "reaction");
        mount(anna);
        mount(ben);
        tick();
        assertTrue(running(), "sitting since before the start is not sitting down");
        host.leaveVehicle();
        later(100);
        pig.addPassenger(host);
        tick();
        assertFalse(running());
        assertEquals(Map.of(id(host), 2), end().drinks(), "the host sat down last");
    }

    /** host already in the state at the start: it counts once a poll has seen the host out of it */
    private void alreadyInTheStateCountsOnlyAfterLeavingIt(String id, Consumer<PlayerMock> enter, Consumer<PlayerMock> leave) {
        enter.accept(host);
        force(id, "reaction");
        int sips = plugin.director().running(round).def.sips();
        tick();
        assertTrue(running(), id + ": in it since before the start is not doing it");
        enter.accept(anna);
        enter.accept(ben);
        later(100);
        tick();
        assertTrue(running(), id + ": the host is still in it from before");
        leave.accept(host);
        later(100);
        tick();
        enter.accept(host);
        later(100);
        tick();
        assertFalse(running(), id + ": done once the host is back in it");
        assertEquals(Map.of(id(host), sips), end().drinks(), id + ": the host was last");
    }

    @Test
    void whoAlreadyHoldsTheTargetMustPutItAwayFirst() {
        alreadyInTheStateCountsOnlyAfterLeavingIt("react_dirt", p -> p.getInventory().setItemInMainHand(new ItemStack(Material.DIRT)),
                p -> p.getInventory().setItemInMainHand(new ItemStack(Material.STONE)));
    }

    @Test
    void whoIsAlreadyInWaterMustGetOutFirst() {
        alreadyInTheStateCountsOnlyAfterLeavingIt("react_water", p -> p.setInWater(true), p -> p.setInWater(false));
    }

    private static void look(PlayerMock p, float pitch) {
        Location to = p.getLocation().clone();
        to.setPitch(pitch);
        move(p, to);
    }

    @Test
    void whoAlreadyLooksUpMustLookAwayFirst() {
        Location up = host.getLocation().clone();
        up.setPitch(-80);
        host.teleport(up);
        force("react_look_up", "reaction");
        move(host, up.clone().add(0.5, 0, 0));   // walking around, still looking up
        tick();
        assertTrue(running(), "looking up since before the start is not looking up");
        for (PlayerMock p : List.of(anna, ben)) {
            look(p, -80);
            later(100);
        }
        tick();
        assertTrue(running(), "the host still looks up from before");
        look(host, 0);
        later(100);
        look(host, -80);
        tick();
        assertFalse(running());
        assertEquals(Map.of(id(host), 1), end().drinks(), "the host looked up again last");
    }

    /** a poll finds Anna and Ben in water at once: one of them was last, not both */
    @Test
    void twoFoundByTheSamePollAreNoTieOneOfThemDrinks() {
        plugin.random(new Random(7));
        force("react_water", "reaction");
        host.setInWater(true);
        tick();
        later(500);
        anna.setInWater(true);
        ben.setInWater(true);
        tick();
        assertFalse(running());
        Map<UUID, Integer> drinks = end().drinks();
        assertEquals(1, drinks.size(), "one of the two: " + drinks);
        assertTrue(Set.of(id(anna), id(ben)).containsAll(drinks.keySet()), "the host was first: " + drinks);
        assertEquals(2, end().winners().size());
    }

    /** two events at the same moment are a real tie */
    @Test
    void crouchingAtTheSameMomentIsATieAndBothDrink() {
        force("react_crouch", "reaction");
        sneak(host, true);
        later(100);
        sneak(anna, true);
        sneak(ben, true);
        tick();
        assertEquals(Map.of(id(anna), 1, id(ben), 1), end().drinks());
    }

    /** the run ends on the first tick after the deadline: what happens between the two counts for nothing */
    @Test
    void whatHappensAfterTheDeadlineDoesNotCount() {
        force("react_water", "reaction");
        anna.setInWater(true);
        tick();
        later(60_000 + 400);
        host.setInWater(true);
        tick();
        assertFalse(running());
        assertEquals(Map.of(id(host), 2, id(ben), 2), end().drinks(), "the host was too late");
        assertEquals(List.of(id(anna)), end().winners());
    }

    @Test
    void aMoveAfterTheDeadlineDoesNotBreakTheFreeze() {
        force("react_freeze", "reaction");
        tick();
        later(2_000);
        tick();   // the grace is over: the positions are taken
        later(8_000 + 400);
        move(ben, ben.getLocation().clone().add(0, 0, 1));
        tick();
        assertFalse(running());
        assertTrue(end().drinks().isEmpty(), "Ben moved when it was over: " + end().drinks());
        assertEquals(3, end().winners().size());
    }
}
