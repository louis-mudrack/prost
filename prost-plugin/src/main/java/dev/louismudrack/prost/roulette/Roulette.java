package dev.louismudrack.prost.roulette;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.api.event.RouletteHitEvent;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.storage.StatsField;
import dev.louismudrack.prost.ui.Sounds;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;

import java.time.Duration;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The random events: an action of an active member who is not in a running challenge may hit them - at most
 * max-hits per window and min-gap apart - then a ~3 s spin lands on 1-4 sips, which go through Sips like a challenge
 * penalty (joker, limit, water). A restart mid-spin drops that spin: nobody drinks, like a challenge cut by a restart.
 */
public final class Roulette implements Listener {
    /** ticks between two frames of the spin: fast, then slowing down (66 ticks = 3.3 s); the last frame is the result */
    private static final int[] SPIN_DELAYS = {2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 8, 10, 12};
    private static final Set<EntityType> BOSSES = Set.of(EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.ELDER_GUARDIAN, EntityType.WARDEN);
    private static final Title.Times FRAME = Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ZERO);
    private static final Title.Times RESULT = Title.Times.times(Duration.ZERO, Duration.ofMillis(1800), Duration.ofMillis(400));
    private static final float PITCH_LOW = 0.8f, PITCH_HIGH = 1.6f;

    private final ProstPlugin plugin;
    private final Random rnd = new Random();

    public Roulette(ProstPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { roll(e.getPlayer(), "block", "break"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { roll(e.getPlayer(), "block", "place"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player p) roll(p, "craft", "craft");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        if (e instanceof PlayerDeathEvent || e.getEntity() instanceof Player) return;   // a player's death is the "death" category
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        if (BOSSES.contains(e.getEntityType())) roll(killer, "boss", "boss");
        else roll(killer, "kill", "kill");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) { roll(e.getPlayer(), "eat", "eat"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) roll(e.getPlayer(), "fish", "fish");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent e) {
        if (e.getBreeder() instanceof Player p) roll(p, "breed", "breed");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent e) { roll(e.getPlayer(), "trade", "trade"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) { roll(e.getEnchanter(), "enchant", "enchant"); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (e.getAdvancement().getDisplay() != null)   // real advancements only - recipe unlocks have no display
            roll(e.getPlayer(), "advancement", "advancement");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) { roll(e.getEntity(), "death", "death"); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent e) {
        if (e.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) roll(e.getPlayer(), "sleep", "sleep");
    }

    /** /prost admin debug: every roll of a round member is logged with its outcome */
    public boolean debug;

    /** a capped roll does not count; the hit is recorded before the spin, so the gap holds during it */
    /** {@code whatKey} names the action in the language files (roulette.what.*) */
    private void roll(Player p, String category, String whatKey) {
        UUID id = p.getUniqueId();
        Round r = plugin.rounds().of(id);
        if (r == null) return;
        if (plugin.hooks().bot(p)) { trace(p, category, "skipped: bot"); return; }
        Member m = r.member(id);
        if (!r.isActive(id)) {
            trace(p, category, "skipped: inactive (round paused " + r.paused + ", self " + m.selfPaused + ", afk " + m.afk + ", away " + m.away + ", mode " + m.wrongMode + ")");
            return;
        }
        if (plugin.director().involves(id)) { trace(p, category, "skipped: in a challenge"); return; }
        RouletteRules.Settings s = plugin.cfg().roulette();
        long now = System.currentTimeMillis();
        RouletteRules.prune(m.rouletteHits, now, s.windowMs());
        if (RouletteRules.capped(m.rouletteHits, now, s)) { trace(p, category, "skipped: capped (" + m.rouletteHits.size() + " in window)"); return; }
        double draw = rnd.nextDouble();
        if (!RouletteRules.triggers(s, category, draw)) { trace(p, category, "no hit (" + draw + " vs " + s.chances().get(category) + ")"); return; }
        m.rouletteHits.add(now);
        int sips = RouletteRules.spin(s.weights(), rnd.nextDouble());
        trace(p, category, "HIT: " + sips);
        spin(r, id, sips, s.weights().length, whatKey);
    }

    private void trace(Player p, String category, String outcome) {
        if (debug) plugin.getLogger().info("[debug] roulette " + p.getName() + " " + category + ": " + outcome);
    }

    private void spin(Round r, UUID id, int sips, int faces, String whatKey) {
        int frames = SPIN_DELAYS.length;
        long at = 0;
        for (int i = 0; i < frames; i++) {
            at += SPIN_DELAYS[i];
            int frame = i;
            int shown = Math.floorMod(sips - 1 - (frames - 1 - i), faces) + 1;   // counts up and lands on the result
            Bukkit.getScheduler().runTaskLater(plugin, () -> frame(id, shown, frame, frames), at);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> land(r, id, sips, whatKey), at);
    }

    private void frame(UUID id, int shown, int frame, int frames) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        boolean last = frame == frames - 1;
        String sub = last ? "<primary>" + Msg.sips(id, shown) + "</primary>" : "<white>" + Msg.sips(id, shown);
        p.showTitle(Title.title(Msg.mm(Msg.str(id, "roulette.title")), Msg.mm(sub), last ? RESULT : FRAME));
        if (last) Sounds.ding(p);
        else Sounds.tick(p, PITCH_LOW + (PITCH_HIGH - PITCH_LOW) * frame / (frames - 1));
    }

    void land(Round r, UUID id, int sips, String whatKey) {
        Member m = r.member(id);
        if (m == null || plugin.rounds().of(id) != r) return;   // left the round (or it ended) during the spin
        Bukkit.getPluginManager().callEvent(new RouletteHitEvent(id, r.code, sips));
        m.tally.roulette++;
        plugin.stats().add(id, m.name, StatsField.ROULETTE, 1);
        plugin.sips().drink(r, id, sips, SipLedger.Source.ROULETTE,
                viewer -> Msg.str(viewer, "roulette.label", V.mm("what", Msg.str(viewer, "roulette.what." + whatKey))));
    }
}
