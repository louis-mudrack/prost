package dev.louismudrack.prost.storage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The online players' settings (language, first-round notice), loaded on join; until loaded nothing is chosen. */
public final class PlayerPrefs implements Listener {
    private final StatsStore store;
    private final Map<UUID, Prefs> cache = new ConcurrentHashMap<>();

    public PlayerPrefs(StatsStore store) { this.store = store; }

    public Optional<String> language(UUID id) {
        Prefs p = cache.get(id);
        return p == null ? Optional.empty() : Optional.ofNullable(p.language());
    }

    public boolean noticed(UUID id) {
        Prefs p = cache.get(id);
        return p != null && p.noticed();
    }

    /** null = back to automatic */
    public void setLanguage(Player p, String code) {
        cache.compute(p.getUniqueId(), (id, old) -> new Prefs(code, old != null && old.noticed()));
        store.setLanguage(p.getUniqueId(), p.getName(), code);
    }

    public void markNoticed(Player p) {
        cache.compute(p.getUniqueId(), (id, old) -> new Prefs(old == null ? null : old.language(), true));
        store.markNoticed(p.getUniqueId(), p.getName());
    }

    /** a change made while loading wins over what was stored */
    public void load(Player p) {
        UUID id = p.getUniqueId();
        store.prefs(id, loaded -> {
            if (p.isOnline()) cache.putIfAbsent(id, loaded);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { load(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { cache.remove(e.getPlayer().getUniqueId()); }
}
