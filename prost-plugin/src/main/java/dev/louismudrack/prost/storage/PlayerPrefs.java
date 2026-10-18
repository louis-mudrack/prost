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
    /** languageSet: chosen in this session, so the stored language must not replace it - automatic (null) counts too */
    private record Entry(String language, boolean languageSet, boolean noticed) {
        static final Entry NONE = new Entry(null, false, false);

        Entry withStored(Prefs stored) {
            return new Entry(languageSet ? language : stored.language(), languageSet, noticed || stored.noticed());
        }
    }

    private final StatsStore store;
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();

    public PlayerPrefs(StatsStore store) { this.store = store; }

    public Optional<String> language(UUID id) {
        Entry e = cache.get(id);
        return e == null ? Optional.empty() : Optional.ofNullable(e.language());
    }

    public boolean noticed(UUID id) {
        Entry e = cache.get(id);
        return e != null && e.noticed();
    }

    /** null = back to automatic */
    public void setLanguage(Player p, String code) {
        cache.compute(p.getUniqueId(), (id, old) -> new Entry(code, true, old != null && old.noticed()));
        store.setLanguage(p.getUniqueId(), p.getName(), code);
    }

    public void markNoticed(Player p) {
        cache.compute(p.getUniqueId(), (id, old) -> {
            Entry e = old == null ? Entry.NONE : old;
            return new Entry(e.language(), e.languageSet(), true);
        });
        store.markNoticed(p.getUniqueId(), p.getName());
    }

    /** a change made while loading wins over what was stored; the rest comes from the stored record */
    public void load(Player p) {
        UUID id = p.getUniqueId();
        store.prefs(id, stored -> {
            if (p.isOnline()) cache.compute(id, (key, old) -> (old == null ? Entry.NONE : old).withStored(stored));
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { load(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { cache.remove(e.getPlayer().getUniqueId()); }
}
