package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.menu.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * The hot potato as an item: Prost's mark on a poisonous potato, and the rules every marked potato follows whether a game
 * runs or not. It is never stored (containers, ender chests, bundles, item frames, allays, armor stands, decorated pots,
 * hoppers), composted, eaten, burnt, destroyed, despawned, carried through a portal or dropped on death; which player
 * may catch a thrown one is the game's call. A potato that outlived its game (a crash) goes when its player joins, its
 * chunk loads, a click finds it in a container or, while a hot potato runs, a hopper moves it.
 */
public final class Potatoes implements Listener {
    /** NamespacedKey(plugin, "potato") of the plugin named Prost, static so isPotato needs no instance */
    private static final NamespacedKey KEY = new NamespacedKey("prost", "potato");

    private final ProstPlugin plugin;
    private final Listener hoppers = new HopperWatch();
    /** the hot potato games running now: hoppers are watched while there is one */
    private final Set<PotatoTracker> games = new HashSet<>();

    public Potatoes(ProstPlugin plugin) { this.plugin = plugin; }

    public static boolean isPotato(ItemStack item) {
        return item != null && item.getType() == Material.POISONOUS_POTATO && item.getPersistentDataContainer().has(KEY);
    }

    /** a new potato, named in {@code lang} */
    public ItemStack create(String lang) {
        ItemStack potato = Items.item(Material.POISONOUS_POTATO, plugin.lang().template(lang, "game.potato.item"),
                plugin.lang().lines(lang, "game.potato.item_lore"));
        potato.editMeta(meta -> meta.getPersistentDataContainer().set(KEY, PersistentDataType.BOOLEAN, true));
        return potato;
    }

    /** from everywhere the player keeps items: inventory, cursor, crafting grid, ender chest */
    public void removeFrom(Player p) {
        removeFrom(p.getInventory());
        removeFrom(p.getEnderChest());
        InventoryView view = p.getOpenInventory();
        // a view without a top inventory has no crafting grid to clear (MockBukkit's own view is one)
        if (!foreign(view) && view.getTopInventory() != null) removeFrom(view.getTopInventory());
        if (isPotato(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    /** InventoryMoveItemEvent comes with every hopper transfer on the server: listened to only while a hot potato runs */
    void started(PotatoTracker game) {
        if (games.add(game) && games.size() == 1) plugin.getServer().getPluginManager().registerEvents(hoppers, plugin);
    }

    /** inline: onDisable stops the games through here */
    void stopped(PotatoTracker game) {
        if (games.remove(game) && games.isEmpty()) HandlerList.unregisterAll(hoppers);
    }

    public void sweepWorlds() {
        for (World w : plugin.getServer().getWorlds()) for (Item item : w.getEntitiesByClass(Item.class)) removeIfPotato(item);
    }

    private static void removeFrom(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) if (isPotato(contents[i])) inventory.setItem(i, null);
    }

    private static void removeIfPotato(Entity entity) { if (isPotatoEntity(entity)) entity.remove(); }

    private static boolean isPotatoEntity(Entity entity) { return entity instanceof Item item && isPotato(item.getItemStack()); }

    // ---------------------------------------------------------------------------------------------- left over

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { removeFrom(e.getPlayer()); }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) { for (Entity entity : e.getEntities()) removeIfPotato(entity); }

    // ---------------------------------------------------------------------------------------------- never stored

    /** a game keeps its own potato out of containers: one clicked in a container is a leftover and goes; any other foreign view only refuses the click */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!touchesPotato(e)) return;
        if (clickedInContainer(e)) {
            e.setCancelled(true);
            removeFrom(e.getClickedInventory());
        } else if (foreign(e.getView()) || isBundle(e.getCurrentItem()) || isBundle(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) { if (isPotato(e.getOldCursor()) && foreign(e.getView())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHopperPickup(InventoryPickupItemEvent e) { if (isPotatoEntity(e.getItem())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHandOver(PlayerInteractEntityEvent e) {
        Entity target = e.getRightClicked();
        if ((target instanceof ItemFrame || target instanceof Allay) && isPotato(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) { if (isPotato(e.getPlayerItem())) e.setCancelled(true); }

    /** blocks that take the held item on a right-click, without an inventory screen */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPutIntoBlock(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && isPotato(e.getItem()) && takesHeldItem(block.getType()))
            e.setUseInteractedBlock(Event.Result.DENY);
    }

    /** the potato on the cursor, under it, or swapped in by a number key or F */
    private static boolean touchesPotato(InventoryClickEvent e) {
        if (isPotato(e.getCurrentItem()) || isPotato(e.getCursor())) return true;
        if (e.getClick() == ClickType.NUMBER_KEY) return isPotato(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()));
        return e.getClick() == ClickType.SWAP_OFFHAND && isPotato(e.getWhoClicked().getInventory().getItemInOffHand());
    }

    /**
     * the clicked potato lies in the open container, not in the player's own inventory or crafting grid; a player's
     * inventory on top (an admin's /invsee, OpenInv) is no container: a game's potato may be there
     */
    private static boolean clickedInContainer(InventoryClickEvent e) {
        Inventory clicked = e.getClickedInventory();
        return isPotato(e.getCurrentItem()) && foreign(e.getView()) && !(clicked instanceof PlayerInventory) && e.getView().getTopInventory().equals(clicked);
    }

    /** any open inventory but the player's own, whose top is the 2x2 crafting grid */
    private static boolean foreign(InventoryView view) { return view.getType() != InventoryType.CRAFTING; }

    /** the plain bundle and the 16 dyed ones */
    private static boolean isBundle(ItemStack item) { return item != null && item.getType().name().endsWith("BUNDLE"); }

    /**
     * A composter takes compostables only, which this potato is not up to 1.21.11; a newer version may change that.
     * Shelves exist from 1.21.9.
     */
    private static boolean takesHeldItem(Material block) {
        return block == Material.DECORATED_POT || block == Material.COMPOSTER || block.name().endsWith("_SHELF");
    }

    // ---------------------------------------------------------------------------------------------- never used up

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEat(PlayerItemConsumeEvent e) { if (isPotato(e.getItem())) e.setCancelled(true); }

    /** before a graves plugin takes the drops */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(item -> isPotato(item));
        e.getItemsToKeep().removeIf(item -> isPotato(item));
    }

    /** a game decides which player may catch its thrown potato; a mob never does, and any other potato on the ground is a leftover */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) {
        Item item = e.getItem();
        boolean byPlayer = e.getEntity() instanceof Player;
        if (!isPotatoEntity(item) || byPlayer && games.stream().anyMatch(game -> game.thrown(item))) return;
        e.setCancelled(true);
        if (byPlayer) item.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(EntityCombustEvent e) { if (isPotatoEntity(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) { if (isPotatoEntity(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDespawn(ItemDespawnEvent e) { if (isPotatoEntity(e.getEntity())) e.setCancelled(true); }

    /** an item crossing a portal is copied into the other world: the game would lose track of it */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(EntityPortalEvent e) { if (isPotatoEntity(e.getEntity())) e.setCancelled(true); }

    /**
     * A potato a hopper moves can only be a leftover: it is taken out of the inventory it leaves. The move is cancelled as
     * well - the moving stack is a copy, which would arrive at the other end.
     */
    static final class HopperWatch implements Listener {
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onHopperMove(InventoryMoveItemEvent e) {
            if (!isPotato(e.getItem())) return;
            e.setCancelled(true);
            removeFrom(e.getSource());
        }
    }
}
