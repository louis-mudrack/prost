package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.ui.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hot potato: a marked potato goes to a random participant, who passes it on by dropping it; whoever holds it when the
 * hidden clock runs out (between half the entry's seconds and all of them) drinks. The holder is the game, the item only
 * pictures it: a full inventory hands it to someone with room, and with no room anywhere the holder keeps it without an
 * item. A dropped potato waits potato.pass-seconds for another active participant, then jumps back to the thrower, who
 * holds it all the while. Potatoes keeps every marked potato out of containers, mouths, fire and death drops.
 */
final class PotatoTracker extends BaseTracker implements GameTracker {
    private static final long SECOND_MS = 1000L;
    /** vanilla lets nobody pick up a thrown item for 2 s, most of the pass window */
    private static final int PICKUP_DELAY_TICKS = 10;

    private final Potatoes potatoes;
    /** participants in the run's order: the winners keep it */
    private final List<UUID> order = new ArrayList<>();
    private long deadline;
    private UUID holder;
    /** the holder's potato on the ground, waiting to be caught; null while it is held */
    private Item thrown;
    private long thrownAt;
    /** the first holder is named on the first poll: the run announces the challenge after starting the tracker */
    private boolean firstHolderTold;

    PotatoTracker(TrackContext ctx) {
        super(ctx);
        potatoes = ctx.plugin().potatoes();
    }

    @Override
    protected void begin(List<Player> ps) {
        long longest = ctx.def().seconds() * SECOND_MS;
        deadline = ctx.plugin().now() + ctx.plugin().random().nextLong(longest / 2, longest + 1);
        for (Player p : ps) order.add(p.getUniqueId());
        if (!order.isEmpty()) handTo(pick(order), order, false);
    }

    @Override
    public long deadline() { return deadline; }

    @Override
    public boolean decided() { return false; }

    @Override
    public boolean hiddenClock() { return true; }

    @Override
    public Verdict verdict() {
        Map<UUID, Integer> drinks = new LinkedHashMap<>();
        List<UUID> winners = new ArrayList<>();
        for (UUID id : order) {
            if (id.equals(holder)) drinks.put(id, ctx.def().sips());
            else winners.add(id);
        }
        return Verdict.of(drinks, winners);
    }

    @Override
    public String status(UUID viewer, UUID who) {
        if (holder == null) return "";
        if (viewer.equals(who) && who.equals(holder)) return Msg.str(viewer, "game.potato.you");
        return Msg.str(viewer, "game.potato.holder", V.of("name", name(holder)));
    }

    @Override
    public void dropped(UUID id) {
        order.remove(id);
        if (id.equals(holder)) passOn(id);
    }

    /** an uncaught potato (or one that fell out of the world) jumps back to the thrower */
    @Override
    public void poll(List<Player> online) {
        if (!firstHolderTold) tellFirstHolder();
        if (thrown != null && (!thrown.isValid() || ctx.plugin().now() >= thrownAt + passMs())) {
            thrown.remove();
            thrown = null;
            handTo(holder, order, true);
        }
    }

    private void tellFirstHolder() {
        firstHolderTold = true;
        if (holder == null) return;
        Msg.round(ctx.round(), "game.potato.passed", V.of("name", name(holder)));
        Player p = Bukkit.getPlayer(holder);
        if (p != null && Arrays.stream(p.getInventory().getContents()).anyMatch(item -> Potatoes.isPotato(item))) got(p);
    }

    @Override
    public void stop() {
        super.stop();
        if (thrown != null) thrown.remove();
        thrown = null;
        for (UUID id : players) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) potatoes.removeFrom(p);
        }
    }

    // ---------------------------------------------------------------------------------------------- passing

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Item item = e.getItemDrop();
        if (!e.getPlayer().getUniqueId().equals(holder) || !Potatoes.isPotato(item.getItemStack())) return;
        item.setInvulnerable(true);
        item.setUnlimitedLifetime(true);
        item.setCanMobPickup(false);
        item.setPickupDelay(PICKUP_DELAY_TICKS);
        thrown = item;
        thrownAt = ctx.plugin().now();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) { if (thrown(e.getItem()) && !catches(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCaught(EntityPickupItemEvent e) {
        // a mob only gets here if another plugin let it: the potato is gone then and poll() hands it back
        if (!thrown(e.getItem()) || !(e.getEntity() instanceof Player p)) return;
        thrown = null;
        becomes(p.getUniqueId(), true);
        got(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) { if (e.getPlayer().getUniqueId().equals(holder)) passOn(holder); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { if (e.getPlayer().getUniqueId().equals(holder)) passOn(holder); }

    private boolean thrown(Item item) { return thrown != null && thrown.getUniqueId().equals(item.getUniqueId()); }

    /** another active participant, within the pass window */
    private boolean catches(LivingEntity who) {
        UUID id = who.getUniqueId();
        return who instanceof Player && !id.equals(holder) && order.contains(id) && ctx.round().isActive(id) && ctx.plugin().now() < thrownAt + passMs();
    }

    /** the holder died or left: a random other participant takes the potato over */
    private void passOn(UUID from) {
        if (thrown != null) thrown.remove();
        thrown = null;
        Player p = Bukkit.getPlayer(from);
        if (p != null) potatoes.removeFrom(p);
        List<UUID> others = order.stream().filter(id -> !id.equals(from)).toList();
        if (!others.isEmpty()) handTo(pick(others), others, true);
    }

    /**
     * To {@code to}, or with a full inventory to a random one of {@code pool} with room; with no room anywhere {@code to}
     * holds it without an item. {@code tell}: the round hears of a new holder, the one who gets the item how to pass it.
     */
    private void handTo(UUID to, List<UUID> pool, boolean tell) {
        UUID taker = hasRoom(to) ? to : pick(pool.stream().filter(id -> hasRoom(id)).toList());
        becomes(taker != null ? taker : to, tell);
        if (taker == null) return;
        Player p = Bukkit.getPlayer(taker);
        p.getInventory().setItem(freeSlot(p), potatoes.create(Msg.lang(taker)));
        if (tell) got(p);
    }

    private void becomes(UUID id, boolean tell) {
        if (id.equals(holder)) return;
        holder = id;
        if (tell) Msg.round(ctx.round(), "game.potato.passed", V.of("name", name(id)));
    }

    private void got(Player p) {
        Msg.tell(p, "game.potato.got", V.of("seconds", ctx.plugin().cfg().games().potatoPassSeconds()));
        Sounds.bell(p);
    }

    private UUID pick(List<UUID> ids) { return ids.isEmpty() ? null : ids.get(ctx.plugin().random().nextInt(ids.size())); }

    /** online, alive and a free slot: the potato never takes the place of another item */
    private static boolean hasRoom(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null && !p.isDead() && freeSlot(p) >= 0;
    }

    private static int freeSlot(Player p) {
        ItemStack[] storage = p.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) if (storage[i] == null || storage[i].isEmpty()) return i;
        return -1;
    }

    private long passMs() { return ctx.plugin().cfg().games().potatoPassSeconds() * SECOND_MS; }

    private String name(UUID id) {
        Member m = ctx.round().member(id);
        return m == null ? "?" : m.name;
    }
}
