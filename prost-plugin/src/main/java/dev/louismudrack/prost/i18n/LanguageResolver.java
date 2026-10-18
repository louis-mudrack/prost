package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.hook.Hooks;
import dev.louismudrack.prost.storage.PlayerPrefs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Which language a player sees (spec §4.2): provider, own choice, client language, server default - first that exists. */
public final class LanguageResolver {
    private final Lang lang;
    private final Hooks hooks;
    private final PlayerPrefs prefs;
    private final boolean perPlayer;

    public LanguageResolver(Lang lang, Hooks hooks, PlayerPrefs prefs, boolean perPlayer) {
        this.lang = lang;
        this.hooks = hooks;
        this.prefs = prefs;
        this.perPlayer = perPlayer;
    }

    public String of(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? of(p) : choose(Optional.empty(), prefs.language(id), null, perPlayer, lang.defaultLanguage(), lang::has);
    }

    public String of(Player p) {
        return choose(hooks.language(p), prefs.language(p.getUniqueId()), p.locale().getLanguage(), perPlayer, lang.defaultLanguage(), lang::has);
    }

    static String choose(Optional<String> provider, Optional<String> stored, String client, boolean perPlayer, String serverDefault,
                         Predicate<String> available) {
        Optional<String> fromProvider = provider.map(LanguageResolver::normalize).filter(available);
        if (fromProvider.isPresent()) return fromProvider.get();
        if (perPlayer) {
            Optional<String> own = stored.map(LanguageResolver::normalize).filter(available);
            if (own.isPresent()) return own.get();
            if (client != null && available.test(normalize(client))) return normalize(client);
        }
        return serverDefault;
    }

    /** "DE-at", "de_AT", " de " -> "de" */
    public static String normalize(String code) {
        String c = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int region = c.indexOf('_');
        return region < 0 ? c : c.substring(0, region);
    }
}
