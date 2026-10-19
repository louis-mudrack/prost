package dev.louismudrack.prost.hook;

import com.earth2me.essentials.IEssentials;
import com.earth2me.essentials.User;
import dev.louismudrack.prost.api.AfkProvider;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** EssentialsX's AFK state (/afk and its idle timer). Loaded only when EssentialsX is enabled; a failure falls back. */
public final class EssentialsAfk implements AfkProvider {
    private final IEssentials essentials;
    private final AfkProvider fallback;
    private final Logger log;
    private volatile boolean warned;

    private EssentialsAfk(IEssentials essentials, AfkProvider fallback, Logger log) {
        this.essentials = essentials;
        this.fallback = fallback;
        this.log = log;
    }

    /** empty when the plugin named Essentials is not EssentialsX: the caller keeps its own detection and says so */
    public static Optional<AfkProvider> of(Plugin essentials, AfkProvider fallback, Logger log) {
        try {
            return essentials instanceof IEssentials ess ? Optional.of(new EssentialsAfk(ess, fallback, log)) : Optional.empty();
        } catch (NoClassDefFoundError e) {   // a plugin by that name without EssentialsX's classes
            return Optional.empty();
        }
    }

    @Override
    public boolean isAfk(Player p) {
        try {
            User user = essentials.getUser(p);
            return user == null ? fallback.isAfk(p) : user.isAfk();
        } catch (RuntimeException | LinkageError e) {
            if (!warned) {
                warned = true;
                log.log(Level.WARNING, "EssentialsX AFK check failed - Prost's own detection answers instead", e);
            }
            return fallback.isAfk(p);
        }
    }
}
