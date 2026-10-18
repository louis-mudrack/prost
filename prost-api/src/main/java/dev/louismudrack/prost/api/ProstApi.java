package dev.louismudrack.prost.api;

import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hooks replace one of Prost's defaults. There is one provider per hook type; a new registration replaces the
 * previous one. When the owning plugin disables or calls {@link #unregisterAll(Plugin)}, Prost's own default answers
 * again (a replaced earlier provider does not come back). A {@code null} plugin or hook throws
 * {@link NullPointerException}. A hook that throws is logged once and Prost's default answers for that call.
 * Call everything from the server's main thread, except {@link #stats(UUID)}.
 */
public interface ProstApi {
    /** which language a player sees (wins over /prost language and the client language) */
    void registerLanguageProvider(Plugin owner, LanguageProvider provider);

    /** AFK players get no challenges and no roulette (default: Prost's movement detection or EssentialsX) */
    void registerAfkProvider(Plugin owner, AfkProvider provider);

    /** bots never play (default: players with the NPC metadata) */
    void registerBotFilter(Plugin owner, BotFilter filter);

    /** whether Nether/End challenges may be drawn (default: a loaded world of that kind and allow-nether / allow-end) */
    void registerDimensionGate(Plugin owner, DimensionGate gate);

    /** positions that never count for reach goals, e.g. a spawn island (default: config.yml excluded-areas) */
    void registerAreaFilter(Plugin owner, AreaFilter filter);

    /** removes every hook the given plugin registered */
    void unregisterAll(Plugin owner);

    /** the round the player is in, if any */
    Optional<RoundView> roundOf(UUID player);

    /** snapshots of all running rounds */
    Collection<RoundView> rounds();

    /**
     * all-time numbers. Completes on a background thread (never the server thread, so joining it there is safe);
     * empty when the player never played, exceptionally when the stats cannot be read. Safe from any thread.
     */
    CompletableFuture<Optional<StatsView>> stats(UUID player);
}
