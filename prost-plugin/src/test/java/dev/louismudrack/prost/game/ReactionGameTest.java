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
}
