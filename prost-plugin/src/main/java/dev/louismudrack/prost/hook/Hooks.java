package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.api.AfkProvider;
import dev.louismudrack.prost.api.AreaFilter;
import dev.louismudrack.prost.api.BotFilter;
import dev.louismudrack.prost.api.DimensionGate;
import dev.louismudrack.prost.api.LanguageProvider;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The places where a server's own plugins may decide for Prost (spec §3.1): one provider per hook, else Prost's
 * default. A provider that throws - including a LinkageError when its plugin's own dependency is missing - or answers
 * null is logged once and the default answers that call, so a broken integration never stops a round.
 */
public final class Hooks implements Listener {
    private static final String NPC_MARKER = "NPC";

    private record Registered<T>(Plugin owner, T hook) {}

    private final Logger log;
    private final AfkProvider defaultAfk;
    private final DimensionGate defaultDimensions;
    private final AreaFilter defaultAreas;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    private volatile Registered<LanguageProvider> language;
    private volatile Registered<AfkProvider> afk;
    private volatile Registered<BotFilter> bots;
    private volatile Registered<DimensionGate> dimensions;
    private volatile Registered<AreaFilter> areas;

    public Hooks(Logger log, AfkProvider defaultAfk, DimensionGate defaultDimensions, AreaFilter defaultAreas) {
        this.log = log;
        this.defaultAfk = defaultAfk;
        this.defaultDimensions = defaultDimensions;
        this.defaultAreas = defaultAreas;
    }

    public void setLanguage(Plugin owner, LanguageProvider p) { language = registered(owner, p, "language"); }

    public void setAfk(Plugin owner, AfkProvider p) { afk = registered(owner, p, "AFK"); }

    public void setBots(Plugin owner, BotFilter f) { bots = registered(owner, f, "bot filter"); }

    public void setDimensions(Plugin owner, DimensionGate g) { dimensions = registered(owner, g, "Nether/End gate"); }

    public void setAreas(Plugin owner, AreaFilter f) { areas = registered(owner, f, "excluded areas"); }

    private <T> Registered<T> registered(Plugin owner, T hook, String what) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(hook, what + " hook");
        log.info(owner.getName() + " provides the " + what + " for Prost");
        warned.removeIf(key -> key.startsWith(what + "/"));
        return new Registered<>(owner, hook);
    }

    public void unregisterAll(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        if (ownedBy(language, owner)) language = null;
        if (ownedBy(afk, owner)) afk = null;
        if (ownedBy(bots, owner)) bots = null;
        if (ownedBy(dimensions, owner)) dimensions = null;
        if (ownedBy(areas, owner)) areas = null;
    }

    private static boolean ownedBy(Registered<?> r, Plugin owner) { return r != null && r.owner().equals(owner); }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) { unregisterAll(e.getPlugin()); }

    public boolean hasLanguageProvider() { return language != null; }

    public Optional<String> language(Player p) {
        Registered<LanguageProvider> r = language;
        if (r == null) return Optional.empty();
        return call("language", r, () -> r.hook().language(p), Optional::empty);
    }

    public boolean afk(Player p) {
        Registered<AfkProvider> r = afk;
        return r == null ? defaultAfk.isAfk(p) : call("AFK", r, () -> r.hook().isAfk(p), () -> defaultAfk.isAfk(p));
    }

    public boolean bot(Player p) {
        Registered<BotFilter> r = bots;
        return r == null ? p.hasMetadata(NPC_MARKER) : call("bot filter", r, () -> r.hook().isBot(p), () -> p.hasMetadata(NPC_MARKER));
    }

    public boolean open(World.Environment env) {
        Registered<DimensionGate> r = dimensions;
        return r == null ? defaultDimensions.isOpen(env) : call("Nether/End gate", r, () -> r.hook().isOpen(env), () -> defaultDimensions.isOpen(env));
    }

    public boolean excluded(Location l) {
        Registered<AreaFilter> r = areas;
        return r == null ? defaultAreas.isExcluded(l) : call("excluded areas", r, () -> r.hook().isExcluded(l), () -> defaultAreas.isExcluded(l));
    }

    private <T> T call(String what, Registered<?> r, Supplier<T> body, Supplier<T> fallback) {
        try {
            T answer = body.get();
            if (answer != null) return answer;
            warnOnce(what, r, "answered null", null);
        } catch (RuntimeException | LinkageError e) {
            warnOnce(what, r, "failed", e);
        }
        return fallback.get();
    }

    private void warnOnce(String what, Registered<?> r, String problem, Throwable cause) {
        if (warned.add(what + "/" + r.owner().getName()))
            log.log(Level.WARNING, r.owner().getName() + "'s " + what + " hook " + problem + " - Prost's default answers instead", cause);
    }
}
