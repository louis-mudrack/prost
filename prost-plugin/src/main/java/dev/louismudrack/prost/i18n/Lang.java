package dev.louismudrack.prost.i18n;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Every loaded language file as flat keys. Lookup: the language, then the server default, then English. */
public final class Lang {
    public static final String FALLBACK = "en";
    private static final String ALIASES = "commands.";
    private static final String CHAT_LINE_BREAK = "<newline>";

    private final Map<String, Map<String, Object>> files;
    private final String defaultLanguage;
    private final Consumer<String> warn;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    /** files: code -> flat key -> String or List<String> */
    public Lang(Map<String, Map<String, Object>> files, String defaultLanguage, Consumer<String> warn) {
        this.files = files;
        this.defaultLanguage = defaultLanguage;
        this.warn = warn;
    }

    public static Lang empty() { return new Lang(Map.of(), FALLBACK, w -> { }); }

    public boolean has(String code) { return files.containsKey(code); }

    public Set<String> languages() { return files.keySet(); }

    public String defaultLanguage() { return defaultLanguage; }

    public String template(String code, String key) {
        Object v = find(code, key);
        return v instanceof List<?> l ? String.join(CHAT_LINE_BREAK, l.stream().map(String::valueOf).toList()) : String.valueOf(v);
    }

    public List<String> lines(String code, String key) {
        Object v = find(code, key);
        return v instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of(String.valueOf(v));
    }

    /** subcommand names of one language: commands.<subcommand>: [names] */
    public Map<String, List<String>> aliases(String code) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        files.getOrDefault(code, Map.of()).forEach((key, value) -> {
            if (key.startsWith(ALIASES) && value instanceof List<?> l) out.put(key.substring(ALIASES.length()), l.stream().map(String::valueOf).toList());
        });
        return out;
    }

    private Object find(String code, String key) {
        for (String c : new String[]{code, defaultLanguage, FALLBACK}) {
            Object v = files.getOrDefault(c, Map.of()).get(key);
            if (v instanceof String || v instanceof List<?>) return v;
        }
        if (warned.add(key)) warn.accept("text '" + key + "' is missing in every language file - showing the key");
        return key;
    }
}
