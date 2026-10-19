package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.event.ChallengeEndEvent;
import dev.louismudrack.prost.tracker.Potatoes;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ItemMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.mockbukkit.mockbukkit.inventory.PlayerInventoryViewMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The hot potato in a round of three, on a clock the test moves by hand. */
class HotPotatoTest {
    private static final long PASS_MS = 3_000L, LONGEST_MS = 120_000L;
    private static final int STORAGE_SLOTS = 36;

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final List<ChallengeEndEvent> ended = new ArrayList<>();
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host, anna, ben;
    private Round round;

    public class Recorder implements Listener {
        @EventHandler public void on(ChallengeEndEvent e) { ended.add(e); }
    }

    /** an item entity that keeps the flags MockBukkit 4.116.3 does not implement */
    static final class DroppedItem extends ItemMock {
        boolean unlimitedLifetime, canMobPickup = true;

        DroppedItem(ServerMock server, ItemStack stack) { super(server, UUID.randomUUID(), stack); }

        @Override public void setUnlimitedLifetime(boolean unlimited) { unlimitedLifetime = unlimited; }
        @Override public boolean isUnlimitedLifetime() { return unlimitedLifetime; }
        @Override public void setCanMobPickup(boolean can) { canMobPickup = can; }
        @Override public boolean canMobPickup() { return canMobPickup; }
    }

    /** every random pick takes the first candidate: the participants in join order, host first */
    static final class FirstPick extends Random {
        @Override public int nextInt(int bound) { return 0; }
    }

    /** the hidden length at one end of its range */
    static final class LengthAt extends Random {
        private final boolean longest;

        LengthAt(boolean longest) { this.longest = longest; }

        @Override public long nextLong(long origin, long bound) { return longest ? bound - 1 : origin; }
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

    private void force() {
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + round.code + " hot_potato reaction");
        assertNotNull(plugin.director().running(round), "hot_potato runs");
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

    private List<PlayerMock> players() { return List.of(host, anna, ben); }

    private static int potatoesOf(PlayerMock p) {
        int n = Potatoes.isPotato(p.getItemOnCursor()) ? 1 : 0;
        for (ItemStack it : p.getInventory().getContents()) if (Potatoes.isPotato(it)) n++;
        return n;
    }

    private long potatoesOnTheGround() {
        return server.getEntities().stream().filter(e -> e instanceof Item i && i.isValid() && Potatoes.isPotato(i.getItemStack())).count();
    }

    private long potatoes() { return potatoesOnTheGround() + players().stream().mapToInt(p -> potatoesOf(p)).sum(); }

    /** the one player with the potato in the inventory */
    private PlayerMock holder() {
        List<PlayerMock> with = players().stream().filter(p -> potatoesOf(p) > 0).toList();
        assertEquals(1, with.size(), "exactly one player carries the potato");
        assertEquals(1, potatoesOf(with.get(0)));
        return with.get(0);
    }

    private PlayerMock otherThan(PlayerMock... not) {
        List<PlayerMock> excluded = List.of(not);
        return players().stream().filter(p -> !excluded.contains(p)).findFirst().orElseThrow();
    }

    private static int potatoSlot(PlayerMock p) {
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) if (Potatoes.isPotato(contents[i])) return i;
        throw new AssertionError(p.getName() + " has no potato");
    }

    /** what the server does on Q: the stack leaves the inventory as an item entity, then PlayerDropItemEvent */
    private DroppedItem drop(PlayerMock p) {
        int slot = potatoSlot(p);
        ItemStack stack = p.getInventory().getItem(slot);
        p.getInventory().setItem(slot, null);
        DroppedItem item = new DroppedItem(server, stack);
        item.setLocation(p.getLocation());
        server.registerEntity(item);
        PlayerDropItemEvent e = new PlayerDropItemEvent(p, item);
        server.getPluginManager().callEvent(e);
        assertFalse(e.isCancelled(), "the potato can be dropped");
        return item;
    }

    /** what the server does when an entity touches an item: EntityPickupItemEvent, then the stack moves */
    private boolean pickUp(LivingEntity who, Item item) {
        EntityPickupItemEvent e = new EntityPickupItemEvent(who, item, 0);
        server.getPluginManager().callEvent(e);
        if (e.isCancelled()) return false;
        if (who instanceof Player p) p.getInventory().addItem(item.getItemStack());
        item.remove();
        return true;
    }

    private <T extends Event & Cancellable> boolean cancelled(T e) {
        server.getPluginManager().callEvent(e);
        return e.isCancelled();
    }

    /** any inventory type, answering getHolder(boolean) as TestServer's do (Prost's menu listener asks on every click) */
    private static Inventory inventory(InventoryHolder holder, int size, InventoryType type) {
        return new InventoryMock(holder, size, type) {
            @Override
            public InventoryHolder getHolder(boolean useSnapshot) { return getHolder(); }
        };
    }

    private static String barName(PlayerMock p) {
        List<BossBar> bars = new ArrayList<>(p.getBossBars());
        assertEquals(1, bars.size(), p.getName() + "'s bars");
        return PlainTextComponentSerializer.plainText().serialize(bars.get(0).name());
    }

    private static void fill(PlayerMock p) {
        for (int i = 0; i < STORAGE_SLOTS; i++) p.getInventory().setItem(i, new ItemStack(Material.STONE, i + 1));
    }

    private static void assertUntouched(PlayerMock p) {
        for (int i = 0; i < STORAGE_SLOTS; i++) assertEquals(new ItemStack(Material.STONE, i + 1), p.getInventory().getItem(i), p.getName() + " slot " + i);
    }

    @Test
    void exactlyOnePotatoGoesToAParticipantAndTheClockIsHidden() {
        for (PlayerMock p : players()) TestServer.messages(p);
        force();
        assertEquals(1, potatoes());
        PlayerMock holder = holder();
        assertEquals("??", plugin.director().running(round).clock());
        tick();
        List<String> told = TestServer.messages(holder);
        int stakes = indexOf(told, "Time: ?? · Whoever holds the potato at the end: 3 sips"), named = indexOf(told, holder.getName() + " holds the hot potato now.");
        assertTrue(stakes >= 0 && named > stakes, "the challenge first, then who holds it: " + told);
        assertTrue(indexOf(told, "You have the hot potato! Drop it (Q) next to another player: they have 3 s to catch it.") > named, told.toString());
    }

    private static int indexOf(List<String> lines, String part) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains(part)) return i;
        return -1;
    }

    @Test
    void theBarsSayWhoHoldsItAndWhoIsSafe() {
        plugin.random(new FirstPick());
        ben.performCommand("prost pause");   // Ben watches
        force();
        tick();
        assertTrue(barName(host).contains("You have the potato! Drop it (Q) near someone"), barName(host));
        assertTrue(barName(anna).contains("Host has the potato"), barName(anna));
        String watching = barName(ben);
        assertTrue(watching.contains("Host holds it") && watching.contains("Anna safe"), watching);
    }

    @Test
    void passingByDropAndPickupMovesTheHolder() {
        force();
        PlayerMock thrower = holder(), catcher = otherThan(thrower);
        DroppedItem potato = drop(thrower);
        assertEquals(1, potatoes(), "on the ground, not in an inventory");
        assertFalse(pickUp(thrower, potato), "the thrower cannot catch it back");
        PlayerMock outsider = server.addPlayer("Outsider");
        assertFalse(pickUp(outsider, potato), "only a participant catches it");
        later(PASS_MS - 100);
        assertTrue(pickUp(catcher, potato), "another participant within the pass window");
        assertEquals(catcher, holder());
        assertEquals(1, potatoes());
        later(LONGEST_MS);
        tick();
        assertEquals(Map.of(id(catcher), 3), end().drinks(), "the catcher holds it now");
    }

    @Test
    void anUncollectedPotatoReturnsToTheThrower() {
        force();
        PlayerMock thrower = holder();
        DroppedItem potato = drop(thrower);
        assertTrue(potato.isInvulnerable() && potato.isUnlimitedLifetime() && !potato.canMobPickup(), "it cannot be destroyed, despawn or be taken by mobs");
        later(PASS_MS + 1_000);
        assertFalse(pickUp(otherThan(thrower), potato), "too late to catch it");
        tick();
        assertEquals(thrower, holder(), "back with the thrower");
        assertEquals(0, potatoesOnTheGround(), "no potato left on the ground");
        assertFalse(potato.isValid());
    }

    @Test
    void thePotatoCannotBeStoredEatenOrPickedUpByHoppers() {
        force();
        PlayerMock h = holder();
        ItemStack potato = h.getInventory().getItem(potatoSlot(h));
        assertTrue(cancelled(new PlayerItemConsumeEvent(h, potato, EquipmentSlot.HAND)), "eating");

        InventoryView chest = h.openInventory(inventory(null, 27, InventoryType.CHEST));
        h.getInventory().setItem(potatoSlot(h), null);
        h.setItemOnCursor(potato);
        assertTrue(cancelled(new InventoryClickEvent(chest, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL)), "into a chest");
        assertTrue(cancelled(new InventoryDragEvent(chest, null, potato, false, Map.of(0, potato))), "dragged into a chest");
        h.setItemOnCursor(null);
        h.getInventory().setItem(0, potato);
        assertTrue(cancelled(new InventoryClickEvent(chest, InventoryType.SlotType.CONTAINER, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0)), "swapped into a chest");
        h.closeInventory();

        InventoryView enderChest = h.openInventory(inventory(h, 27, InventoryType.ENDER_CHEST));
        assertTrue(cancelled(new InventoryClickEvent(enderChest, InventoryType.SlotType.CONTAINER, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0)), "into the ender chest");
        h.closeInventory();

        // the server's own view has the 2x2 crafting grid on top; MockBukkit's default view has no top inventory
        InventoryView own = new PlayerInventoryViewMock(h, inventory(h, 5, InventoryType.CRAFTING));
        assertEquals(InventoryType.CRAFTING, own.getType());
        assertFalse(cancelled(new InventoryClickEvent(own, InventoryType.SlotType.QUICKBAR, rawSlotOf(own, 0), ClickType.LEFT, InventoryAction.PICKUP_ALL)),
                "moving it around one's own inventory is fine");
        h.getInventory().setItem(1, new ItemStack(Material.BUNDLE));
        h.getInventory().setItem(0, null);
        h.setItemOnCursor(potato);
        assertTrue(cancelled(new InventoryClickEvent(own, InventoryType.SlotType.QUICKBAR, rawSlotOf(own, 1), ClickType.RIGHT, InventoryAction.PICKUP_SOME)), "into a bundle");

        Item onTheGround = h.getWorld().dropItem(h.getLocation(), potato);
        Inventory hopper = inventory(null, 5, InventoryType.HOPPER);
        assertTrue(cancelled(new InventoryPickupItemEvent(hopper, onTheGround)), "a hopper picking it up");
        assertTrue(cancelled(new InventoryMoveItemEvent(inventory(null, 27, InventoryType.CHEST), potato, hopper, true)), "a hopper moving it");

        h.setItemOnCursor(null);
        h.getInventory().setItemInMainHand(potato);
        ItemFrame frame = h.getWorld().spawn(h.getLocation(), ItemFrame.class);
        assertTrue(cancelled(new PlayerInteractEntityEvent(h, frame, EquipmentSlot.HAND)), "into an item frame");
    }

    /** the real server burns, damages and despawns item entities; MockBukkit does not, so the events are fired by hand */
    @Test
    void aPotatoOnTheGroundCannotBurnBreakDespawnOrBeTakenByMobs() {
        force();
        DroppedItem potato = drop(holder());
        assertTrue(cancelled(new EntityCombustEvent(potato, 5f)), "fire");
        assertTrue(cancelled(new EntityDamageEvent(potato, EntityDamageEvent.DamageCause.LAVA, DamageSource.builder(DamageType.LAVA).build(), 4)), "lava");
        assertTrue(cancelled(new ItemDespawnEvent(potato, potato.getLocation())), "despawning");
        Zombie zombie = potato.getWorld().spawn(potato.getLocation(), Zombie.class);
        assertFalse(pickUp(zombie, potato), "a mob");
        Item noGames = potato.getWorld().dropItem(potato.getLocation(), plugin.potatoes().create("en"));
        assertFalse(pickUp(zombie, noGames), "a mob, also with no game watching that potato");
        assertTrue(cancelled(new EntityPortalEvent(noGames, noGames.getLocation(), noGames.getLocation().add(8, 0, 8))), "through a portal");
    }

    @Test
    void aFullInventoryPassesItOnAndNothingIsLost() {
        plugin.random(new FirstPick());
        fill(host);
        force();
        assertUntouched(host);
        assertEquals(0, potatoesOf(host), "the first pick has no room");
        assertEquals(anna, holder(), "the next participant with room takes it");
        later(LONGEST_MS);
        tick();
        assertEquals(Map.of(id(anna), 3), end().drinks());
        assertUntouched(host);
    }

    @Test
    void nobodyWithRoomLeavesTheFirstPickHoldingItUntilThereIsRoom() {
        plugin.random(new FirstPick());
        for (PlayerMock p : players()) fill(p);
        force();
        assertEquals(0, potatoes(), "no room anywhere: no item");
        tick();
        assertTrue(barName(host).contains("You have the potato"), barName(host));
        assertEquals(0, potatoes(), "still no room");
        for (PlayerMock p : players()) assertUntouched(p);
        anna.getInventory().setItem(7, null);
        tick();
        assertEquals(0, potatoes(), "room with somebody else does not move it: the host holds it");
        ItemStack freed = host.getInventory().getItem(5);
        host.getInventory().setItem(5, null);
        tick();
        assertEquals(host, holder(), "the first poll with room hands the host the item");
        assertTrue(Potatoes.isPotato(host.getInventory().getItem(5)));
        host.getInventory().setItem(5, freed);
        assertUntouched(host);
        later(LONGEST_MS);
        tick();
        assertEquals(Map.of(id(host), 3), end().drinks());
    }

    @Test
    void anInactiveParticipantIsNeverHandedThePotato() {
        plugin.random(new FirstPick());
        force();
        assertEquals(host, holder());
        anna.performCommand("prost pause");   // inactive now, dropped by the run only on its next tick
        host.setHealth(0);
        assertEquals(ben, holder(), "the first pick among the others would be Anna, who is paused");
        assertEquals(0, potatoesOf(anna));
    }

    @Test
    void oneParticipantLeftCancelsItAndNobodyDrinks() {
        force();
        for (PlayerMock p : players()) TestServer.messages(p);
        anna.performCommand("prost pause");
        ben.performCommand("prost pause");
        tick();
        assertFalse(running());
        assertEquals(1, ended.size());
        assertFalse(ended.get(0).resolved(), "cancelled, not resolved");
        assertTrue(ended.get(0).drinks().isEmpty(), "nobody drinks");
        assertEquals(0, potatoes(), "every potato is gone");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Fewer than two players are left for the hot potato - cancelled, nobody drinks.")));
    }

    @Test
    void aComposterOrADecoratedPotCannotTakeIt() {
        force();
        PlayerMock h = holder();
        ItemStack potato = h.getInventory().getItem(potatoSlot(h));
        for (Material block : List.of(Material.COMPOSTER, Material.DECORATED_POT)) {
            Block b = h.getWorld().getBlockAt(h.getLocation().getBlockX() + 2, h.getLocation().getBlockY(), h.getLocation().getBlockZ());
            b.setType(block);
            PlayerInteractEvent e = new PlayerInteractEvent(h, Action.RIGHT_CLICK_BLOCK, potato, b, BlockFace.UP, EquipmentSlot.HAND);
            server.getPluginManager().callEvent(e);
            assertEquals(Event.Result.DENY, e.useInteractedBlock(), block + " would take it");
        }
    }

    @Test
    void theHolderDyingOrLeavingPassesItAndDropsNoPotato() {
        List<ItemStack> drops = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void on(PlayerDeathEvent e) { drops.addAll(e.getDrops()); }
        }, plugin);
        host.getWorld().setGameRule(GameRules.KEEP_INVENTORY, true);
        force();
        PlayerMock dead = holder();
        dead.setHealth(0);
        assertTrue(drops.stream().noneMatch(it -> Potatoes.isPotato(it)), "not in the death drops");
        assertEquals(0, potatoesOf(dead), "not kept through keepInventory either");
        PlayerMock heir = holder();
        assertNotEquals(dead, heir);
        assertEquals(1, potatoes());
        dead.respawn();

        heir.performCommand("prost pause");
        tick();
        assertEquals(0, potatoesOf(heir), "who pauses hands it over");
        PlayerMock next = holder();
        assertNotEquals(heir, next);

        next.disconnect();
        assertEquals(0, potatoesOf(next), "who quits takes none along");
        PlayerMock last = otherThan(heir, next);
        assertEquals(last, holder(), "the one still playing has it");
        assertEquals(0, potatoesOnTheGround());
    }

    @Test
    void theHiddenLengthLiesBetweenHalfTheEntrysSecondsAndAllOfThem() {
        for (boolean longest : new boolean[]{false, true}) {
            ended.clear();
            plugin.random(new LengthAt(longest));
            force();
            long length = longest ? LONGEST_MS : LONGEST_MS / 2;
            later(length - 1);
            tick();
            assertTrue(running(), "still running 1 ms before " + length + " ms");
            later(1);
            tick();
            assertFalse(running(), "over at " + length + " ms");
            assertEquals(1, end().drinks().size());
        }
    }

    @Test
    void theHolderAtTheEndDrinksAndNoPotatoRemains() {
        force();
        PlayerMock thrower = holder();
        later(LONGEST_MS / 2 - 1_000);
        tick();
        assertTrue(running(), "it runs at least half the entry's seconds");
        drop(thrower);
        later(LONGEST_MS / 2 + 1_000);
        tick();
        assertFalse(running(), "it ends by the entry's seconds");
        assertEquals(Map.of(id(thrower), 3), end().drinks(), "still in the air: the thrower holds it");
        assertEquals(Stream.of(host, anna, ben).filter(p -> p != thrower).map(p -> id(p)).toList(), end().winners());
        assertEquals(0, potatoes(), "no potato in any inventory or world");
    }

    @Test
    void aPotatoLeftByACrashIsRemovedOnJoin() {
        PlayerMock carl = new PlayerMock(server, "Carl");
        carl.getInventory().addItem(plugin.potatoes().create("en"), new ItemStack(Material.BREAD));
        server.addPlayer(carl);
        assertEquals(0, potatoesOf(carl));
        assertTrue(carl.getInventory().contains(Material.BREAD), "the player's own items stay");

        Item lying = host.getWorld().dropItem(host.getLocation(), plugin.potatoes().create("en"));
        server.getPluginManager().callEvent(new EntitiesLoadEvent(host.getLocation().getChunk(), List.<Entity>of(lying)));
        assertFalse(lying.isValid(), "a potato in a chunk that loads");
    }

    @Test
    void potatoesLeftByACrashAreRemovedOnStartUp() {
        MockBukkit.unmock();
        server = TestServer.mock();
        List<PlayerMock> carl = new ArrayList<>();
        List<Item> lying = new ArrayList<>();
        TestServer.start(server, folder -> {
            Potatoes potatoes = new Potatoes((ProstPlugin) server.getPluginManager().getPlugin("Prost"));
            PlayerMock p = server.addPlayer("Carl");
            p.getInventory().addItem(potatoes.create("en"));
            carl.add(p);
            Location at = p.getLocation();
            lying.add(at.getWorld().dropItem(at, potatoes.create("en")));
        });
        assertEquals(0, potatoesOf(carl.get(0)));
        assertFalse(lying.get(0).isValid());
    }

    /** the raw slot MockBukkit's view reads a player slot from (InventoryClickEvent#getCurrentItem): the bottom right after the top */
    private static int rawSlotOf(InventoryView view, int playerSlot) { return view.getTopInventory().getSize() + playerSlot; }
}
