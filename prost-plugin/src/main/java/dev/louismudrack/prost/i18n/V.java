package dev.louismudrack.prost.i18n;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A named value for a text: <name> in the language file. of() escapes (names, typed rules), mm() inserts markup. */
public record V(String name, String value) {
    private static final Pattern TAG = Pattern.compile("(?<!\\\\)((?:\\\\\\\\)*)<([a-z][a-z0-9_]*)>");

    public static V of(String name, Object value) { return new V(name, escape(String.valueOf(value))); }

    public static V mm(String name, String miniMessage) { return new V(name, miniMessage); }

    /** one pass over the template: an inserted value is never scanned again, so a player's "<player>" stays text; an escaped tag (odd backslashes) is text too */
    public static String fill(String template, V... vars) {
        if (vars.length == 0) return template;
        Map<String, String> byName = new HashMap<>();
        for (V v : vars) byName.put(v.name, v.value);
        Matcher m = TAG.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = byName.get(m.group(2));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : m.group(1) + value));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * MiniMessage's escapeTags only escapes tags it knows, so a typed theme or value tag (<hl>, <player>) would survive
     * it: every '<' is escaped, and every backslash first so a trailing one cannot cancel the next escape.
     */
    public static String escape(String text) { return text.replace("\\", "\\\\").replace("<", "\\<"); }
}
