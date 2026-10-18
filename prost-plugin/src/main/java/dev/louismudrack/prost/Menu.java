package dev.louismudrack.prost;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The UX standard's GUI skeleton (§3): filler everywhere, "» Title" in the brand gradient, Back bottom-left, Close
 * bottom-right, sounds. Clicks are routed by this holder and the slot's action - never by the title.
 */
abstract class Menu implements InventoryHolder {
    private static final int CLOCK_SLOT = 4;
    private static final long CLOCK_TICKS = 20L;

    protected final ProstPlugin plugin;
    protected final Player viewer;
    protected final boolean de;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private Inventory inv;
    /** a timed decision (reward, rule, pick next, hand-out): when it runs out; 0 = no time limit */
    private long deadline;
    private BukkitTask clock;

    Menu(ProstPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.de = Msg.de(viewer.getUniqueId());
    }

    protected abstract int size();

    protected abstract String title();

    protected abstract void draw();

    /** where Back leads; null = no Back button */
    protected Menu back() { return null; }

    /** the inventory closed (by the player or by us) */
    protected void closed() { }

    /** shows a countdown in the top row while the menu is open (test game: a timeout looked like a wrong pick) */
    final Menu until(long deadlineMs) {
        deadline = deadlineMs;
        return this;
    }

    final void open() {
        inv = Bukkit.createInventory(this, size(), Msg.mm("<gradient:#ffd94d:#ff8f3f>» " + Msg.esc(title()) + "</gradient>"));
        render();
        viewer.openInventory(inv);
        Sounds.open(viewer);
        if (deadline > 0 && clock == null) clock = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickClock(), CLOCK_TICKS, CLOCK_TICKS);
    }

    final boolean isOpen() { return viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder(false) == this; }

    private void tickClock() {
        if (!isOpen()) {
            clock.cancel();
            clock = null;
            return;
        }
        inv.setItem(CLOCK_SLOT, clockItem());
    }

    private ItemStack clockItem() {
        long seconds = Math.max(0, (deadline - System.currentTimeMillis() + 999) / 1000);
        return Items.item(Material.CLOCK, "<gray>" + t("Noch ", "Time left: ") + Msg.HL + seconds + " s",
                List.of("<dark_gray>" + t("Danach wird für dich gewählt.", "Then it is chosen for you.")));
    }

    final void render() {
        actions.clear();
        for (int i = 0; i < size(); i++) inv.setItem(i, Items.filler());
        if (deadline > 0) inv.setItem(CLOCK_SLOT, clockItem());
        draw();
        Menu b = back();
        if (b != null) set(size() - 9, Items.item(Material.ARROW, "<gray>" + t("Zurück", "Back"), List.of()), e -> b.open());
        set(size() - 1, Items.item(Material.BARRIER, "<#ff5555>" + t("Schließen", "Close"), List.of()), e -> viewer.closeInventory());
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    final void click(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> action = actions.get(e.getRawSlot());
        if (action == null) return;
        Sounds.click(viewer);
        action.accept(e);
    }

    protected final String t(String german, String english) { return de ? german : english; }

    @Override
    public Inventory getInventory() { return inv; }
}
