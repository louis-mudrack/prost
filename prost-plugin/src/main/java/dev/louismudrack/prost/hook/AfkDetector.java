package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.api.AfkProvider;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Prost's own AFK detection: no movement, look, chat or command for config.yml afk.minutes. */
public final class AfkDetector implements AfkProvider, Listener {
    private final IntSupplier minutes;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();

    public AfkDetector(IntSupplier minutes, LongSupplier clock) {
        this.minutes = minutes;
        this.clock = clock;
    }

    @Override
    public boolean isAfk(Player p) {
        long now = clock.getAsLong();
        return AfkRules.isAfk(lastActive.getOrDefault(p.getUniqueId(), now), now, minutes.getAsInt());
    }

    private void active(Player p) { lastActive.put(p.getUniqueId(), clock.getAsLong()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom(), to = e.getTo();
        Player p = e.getPlayer();
        if (AfkRules.activity(to.getX() - from.getX(), to.getZ() - from.getZ(), to.getYaw() - from.getYaw(),
                to.getPitch() - from.getPitch(), ((org.bukkit.entity.Entity) p).isOnGround(), p.isInsideVehicle())) active(p);
    }

    /**
     * chat arrives on its own thread, possibly after the quit: only an entry that is still there moves on (no entry
     * reads as active anyway)
     */
    private void stillActive(Player p) { lastActive.replace(p.getUniqueId(), clock.getAsLong()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) { stillActive(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) { stillActive(e.getPlayer()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { active(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { lastActive.remove(e.getPlayer().getUniqueId()); }

    int tracked() { return lastActive.size(); }
}
