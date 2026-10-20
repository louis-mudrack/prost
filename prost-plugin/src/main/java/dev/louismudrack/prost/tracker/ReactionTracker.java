package dev.louismudrack.prost.tracker;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.reward.Rewards;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Do it now: a participant is done at the first moment (ProstPlugin#now) they crouch, are in water, say the word, look
 * up, jump, hold a target, sit or eat. Events give that moment exactly; water, looking up and holding are also polled,
 * as they can begin without an event of their own (water flowing in, an item put into the hand). Whoever is in water,
 * looks up or holds a target when it starts has to leave that state first, as a crouch needs a fresh toggle. Freeze is
 * the other way round: moving the body after the grace breaks it. Nothing after the deadline counts. Chat arrives on the
 * async chat thread: it only touches the concurrent doneAt map, which the run reads on the main thread.
 */
final class ReactionTracker extends BaseTracker implements GameTracker {
    private static final String DEFAULT_WORD = "Prost";
    private static final float LOOK_UP_PITCH = -75f;
    /** under 0.1 block is a nudge (another player's collision), not a step */
    private static final double STEP_SQUARED = 0.01;
    private static final long SECOND_MS = 1000L;

    private final String mode;
    private final boolean freeze;
    private final String word;
    private final Set<Material> targets;
    /** participants in the run's order: the winners keep it */
    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, ReactionRules.Done> doneAt = new ConcurrentHashMap<>();
    /** in the state when it started: they count once they have been seen out of it */
    private final Set<UUID> already = new HashSet<>();
    private final Set<UUID> broken = new HashSet<>();
    private final Map<UUID, Location> frozenAt = new HashMap<>();
    private boolean positionsTaken;
    private long deadline, graceEndsAt;

    ReactionTracker(TrackContext ctx) {
        super(ctx);
        mode = ctx.def().mode();
        freeze = "freeze".equals(mode);
        word = ctx.def().value().isBlank() ? DEFAULT_WORD : ctx.def().value().trim();
        targets = materials(ctx.def().targets());
    }

    @Override
    protected void begin(List<Player> ps) {
        long now = ctx.plugin().now();
        deadline = now + ctx.def().seconds() * SECOND_MS;
        graceEndsAt = now + ctx.plugin().cfg().games().freezeGraceSeconds() * SECOND_MS;
        for (Player p : ps) {
            order.add(p.getUniqueId());
            if (inState(p)) already.add(p.getUniqueId());
        }
    }

    @Override
    public long deadline() { return deadline; }

    @Override
    public boolean decided() { return ReactionRules.decided(freeze, order, doneAt, broken); }

    @Override
    public Verdict verdict() {
        return ReactionRules.verdict(ctx.kind(), freeze, order, doneAt, broken, deadline, ctx.def().sips(), ctx.plugin().random());
    }

    @Override
    public void dropped(UUID id) { order.remove(id); }

    /** "don't move" is won by everybody who holds still, not lost by the last one; a Solo keeps the solo line */
    @Override
    public String stakes(UUID viewer) {
        if (!freeze || ctx.kind() != Kind.REACTION) return null;
        return Msg.str(viewer, "challenge.stakes.freeze", V.mm("sips", Msg.sips(viewer, ctx.def().sips())),
                V.mm("reward", Rewards.offered(viewer, ctx.def(), false)));
    }

    @Override
    public int progress(UUID id) { return (freeze ? !broken.contains(id) : doneAt.containsKey(id)) ? 1 : 0; }

    @Override
    public boolean broken(UUID id) { return broken.contains(id); }

    @Override
    public String status(UUID viewer, UUID who) {
        if (freeze) return Msg.str(viewer, broken.contains(who) ? "challenge.progress.broken" : "challenge.progress.holding");
        if (doneAt.containsKey(who)) return Msg.str(viewer, "game.reaction.done");
        if (viewer.equals(who)) return Msg.str(viewer, "game.reaction.hint", V.of("goal", ctx.def().text(Msg.lang(viewer))));
        return Msg.str(viewer, "game.reaction.waiting");
    }

    /** everybody this poll finds gets its time: who did it first since the last poll is not known */
    @Override
    public void poll(List<Player> online) {
        long now = ctx.plugin().now();
        if (freeze && !positionsTaken && now >= graceEndsAt) {
            for (Player p : online) frozenAt.put(p.getUniqueId(), p.getLocation());
            positionsTaken = true;
        }
        for (Player p : online) {
            if (freeze) breaks(p, p.getLocation());
            else seen(p, inState(p), now, true);
        }
    }

    /** the states a poll can see; the other modes are actions, seen by their events only */
    private boolean inState(Player p) {
        return switch (mode) {
            case "water" -> p.isInWater();
            case "look_up" -> looksUp(p.getLocation());
            case "hold" -> holds(p.getInventory().getItemInMainHand());
            default -> false;
        };
    }

    /** a state seen in or out; in it counts unless they have been in it since the start */
    private void seen(Player p, boolean in, long at, boolean polled) {
        if (!in) already.remove(p.getUniqueId());
        else if (!already.contains(p.getUniqueId())) done(p, at, polled);
    }

    private void done(Player p, long at, boolean polled) {
        if (!freeze && tracks(p)) doneAt.putIfAbsent(p.getUniqueId(), new ReactionRules.Done(at, polled));
    }

    private void doneNow(Player p) { done(p, ctx.plugin().now(), false); }

    private void breaks(Player p, Location at) {
        if (ctx.plugin().now() <= deadline && moved(p.getUniqueId(), at)) broken.add(p.getUniqueId());
    }

    private static boolean looksUp(Location at) { return at.getPitch() <= LOOK_UP_PITCH; }

    private boolean holds(ItemStack item) { return item != null && targets.contains(item.getType()); }

    private boolean moved(UUID id, Location now) {
        Location still = frozenAt.get(id);
        return still != null && (!Objects.equals(still.getWorld(), now.getWorld()) || still.distanceSquared(now) > STEP_SQUARED);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) { if ("crouch".equals(mode) && e.isSneaking()) doneNow(e.getPlayer()); }

    /** cancelled too: a chat plugin that cancels the event to send the message itself must not make the word unsayable */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        if ("chat".equals(mode) && PlainTextComponentSerializer.plainText().serialize(e.message()).trim().equalsIgnoreCase(word)) doneNow(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent e) { if ("jump".equals(mode)) doneNow(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) { if ("eat".equals(mode) && e.getItem().getType().isEdible()) doneNow(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if ("hold".equals(mode)) seen(p, holds(p.getInventory().getItem(e.getNewSlot())), ctx.plugin().now(), false);
    }

    /**
     * a mount is always a new seat: a rider is taken off the old one first, so sitting since the start never counts. Boats
     * and minecarts too: VehicleEnterEvent comes before this one, so a seat another plugin refuses here would count there.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent e) { if ("sit".equals(mode) && e.getEntity() instanceof Player p) doneNow(p); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        switch (mode) {
            case "water" -> seen(p, p.isInWater(), ctx.plugin().now(), false);
            case "look_up" -> seen(p, looksUp(e.getTo()), ctx.plugin().now(), false);
            case "freeze" -> breaks(p, e.getTo());
            default -> { }
        }
    }
}
