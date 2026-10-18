package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.config.PluginConfig;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Theme tags for every text (config.yml theme): <primary>...</primary> is a gradient, <hl> <ok> <err> <muted> switch
 * the colour. A tag a player typed arrives escaped (\<hl>) and stays text.
 */
public final class Theme {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern GRADIENT = Pattern.compile("#[0-9a-fA-F]{6}:#[0-9a-fA-F]{6}");
    /** MiniMessage's rule: a tag behind an odd number of backslashes is escaped text, behind an even number it is a tag */
    private static final String NOT_ESCAPED = "(?<!\\\\)((?:\\\\\\\\)*)";
    private static final Pattern OPEN_PRIMARY = tag("<primary>");
    private static final Pattern CLOSE_PRIMARY = tag("</primary>");
    private static final Pattern HL = tag("<hl>");
    private static final Pattern OK = tag("<ok>");
    private static final Pattern ERR = tag("<err>");
    private static final Pattern MUTED = tag("<muted>");

    public static final Theme DEFAULT = of(PluginConfig.DEFAULTS.theme(), w -> { });

    private final String primary, hl, ok, err, muted;

    private Theme(String primary, String hl, String ok, String err, String muted) {
        this.primary = primary;
        this.hl = hl;
        this.ok = ok;
        this.err = err;
        this.muted = muted;
    }

    public static Theme of(PluginConfig.ThemeColors c, Consumer<String> warn) {
        PluginConfig.ThemeColors d = PluginConfig.DEFAULTS.theme();
        String p = c.primary().trim();
        String gradient = GRADIENT.matcher(p).matches() ? p : fallback(warn, "primary", c.primary(), d.primary());
        return new Theme("<gradient:" + gradient + ">", color(c.highlight(), d.highlight(), "highlight", warn),
                color(c.ok(), d.ok(), "ok", warn), color(c.error(), d.error(), "error", warn), color(c.muted(), d.muted(), "muted", warn));
    }

    private static String color(String value, String fallback, String key, Consumer<String> warn) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (HEX.matcher(v).matches() || NamedTextColor.NAMES.value(v) != null) return "<" + v + ">";
        return "<" + fallback(warn, key, value, fallback) + ">";
    }

    private static String fallback(Consumer<String> warn, String key, String value, String fallback) {
        warn.accept("theme." + key + ": '" + value + "' is not a colour (#rrggbb or a name like gold) - using " + fallback);
        return fallback;
    }

    private static Pattern tag(String tag) { return Pattern.compile(NOT_ESCAPED + Pattern.quote(tag)); }

    private static String replace(Pattern tag, String in, String with) { return tag.matcher(in).replaceAll("$1" + Matcher.quoteReplacement(with)); }

    public String apply(String mini) {
        String s = replace(OPEN_PRIMARY, mini, primary);
        s = replace(CLOSE_PRIMARY, s, "</gradient>");
        s = replace(HL, s, hl);
        s = replace(OK, s, ok);
        s = replace(ERR, s, err);
        return replace(MUTED, s, muted);
    }
}
