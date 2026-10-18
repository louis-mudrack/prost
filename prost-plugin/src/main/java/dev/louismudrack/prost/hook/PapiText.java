package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.i18n.V;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** %placeholders% in Prost's own texts, per viewer. Only touched when PlaceholderAPI is enabled. */
public final class PapiText {
    private static final Pattern TOKEN = Pattern.compile("%[^%\\s_]+_[^%\\s]+%");

    private PapiText() {}

    public static BiFunction<Player, String, String> resolver() {
        return (player, text) -> substitute(text, token -> PlaceholderAPI.setPlaceholders(player, token));
    }

    /** Another plugin's output is untrusted (nicknames): its markup stays literal, only its colour codes count. */
    static String substitute(String text, UnaryOperator<String> resolveToken) {
        if (text.indexOf('%') < 0) return text;
        return TOKEN.matcher(text).replaceAll(m -> {
            String value = resolveToken.apply(m.group());
            return Matcher.quoteReplacement(value.equals(m.group()) ? value : LegacyColors.toMiniMessage(V.escape(value)));
        });
    }
}
