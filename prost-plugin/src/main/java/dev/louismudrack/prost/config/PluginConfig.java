package dev.louismudrack.prost.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** config.yml (spec §6): general settings. Missing or invalid values fall back to the defaults with one warning each. */
public record PluginConfig(String defaultLanguage, boolean perPlayerLanguage, ThemeColors theme, List<String> commandAliases,
                           Set<String> worlds, List<AreaBox> excludedAreas, DimensionMode nether, DimensionMode end,
                           int afkMinutes, boolean useEssentials, boolean parsePlaceholders, boolean firstRoundNotice,
                           Storage storage, boolean metrics, boolean updateChecker) {

    public enum DimensionMode {
        AUTO, ON, OFF;

        /** YAML 1.1 reads a bare on/off/yes/no as a boolean - those mean ON/OFF here */
        static DimensionMode of(Object v, Consumer<String> warn, String key) {
            if (v == null) return AUTO;
            if (v instanceof Boolean b) return b ? ON : OFF;
            for (DimensionMode m : values()) if (m.name().equalsIgnoreCase(String.valueOf(v).trim())) return m;
            warn.accept(key + ": '" + v + "' is not auto, on or off - using auto");
            return AUTO;
        }
    }

    public record ThemeColors(String primary, String highlight, String ok, String error, String muted) {}

    public record Storage(String type, String host, int port, String database, String user, String password, String tablePrefix,
                          String parameters) {
        public boolean mysql() { return "mysql".equals(type); }

        @Override
        public String toString() {
            return "Storage[type=" + type + ", host=" + host + ", port=" + port + ", database=" + database + ", user=" + user
                    + ", password=***, tablePrefix=" + tablePrefix + ", parameters=" + parameters + "]";
        }
    }

    private static final Pattern LANGUAGE = Pattern.compile("[a-z]{2,3}");
    private static final Pattern ALIAS = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern TABLE_PREFIX = Pattern.compile("([A-Za-z_][A-Za-z0-9_]{0,15})?");
    private static final String MAIN_COMMAND = "prost";
    private static final int DEFAULT_PORT = 3306, MAX_PORT = 65535;

    public static final PluginConfig DEFAULTS = parse(Map.of(), w -> {});

    public static PluginConfig parse(Map<?, ?> root, Consumer<String> warn) {
        Map<?, ?> r = root == null ? Map.of() : root;
        Map<?, ?> language = sub(r, "language"), theme = sub(r, "theme"), commands = sub(r, "commands"), dims = sub(r, "dimensions"),
                afk = sub(r, "afk"), papi = sub(r, "placeholderapi"), notices = sub(r, "notices"), storage = sub(r, "storage"),
                mysql = sub(storage, "mysql");

        String lang = str(language, "default", "en").trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGE.matcher(lang).matches()) {
            warn.accept("language.default: '" + lang + "' is not a language code like en or de - using en");
            lang = "en";
        }
        ThemeColors colors = new ThemeColors(str(theme, "primary", "#ffd94d:#ff8f3f"), str(theme, "highlight", "#ffd94d"),
                str(theme, "ok", "#55e07a"), str(theme, "error", "#ff5555"), str(theme, "muted", "dark_gray"));

        List<String> aliases = new ArrayList<>();
        Object rawAliases = commands.containsKey("aliases") ? commands.get("aliases") : List.of("drinks");
        if (rawAliases instanceof List<?> l) for (Object o : l) {
            String a = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
            if (!ALIAS.matcher(a).matches() || a.equals(MAIN_COMMAND)) {
                warn.accept("commands.aliases: '" + o + "' skipped (letters, digits, _ and - only; not 'prost')");
                continue;
            }
            if (!aliases.contains(a)) aliases.add(a);
        }

        Set<String> worlds = new LinkedHashSet<>();
        if (r.get("worlds") instanceof List<?> l) for (Object o : l) worlds.add(String.valueOf(o));

        List<AreaBox> areas = new ArrayList<>();
        if (r.get("excluded-areas") instanceof List<?> l) for (Object o : l) {
            AreaBox b = AreaBox.parse(o);
            if (b == null) warn.accept("excluded-areas: " + o + " skipped (needs world, min: [x, y, z], max: [x, y, z])");
            else areas.add(b);
        }

        String type = str(storage, "type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if (!type.equals("sqlite") && !type.equals("mysql")) {
            warn.accept("storage.type: '" + type + "' is not sqlite or mysql - using sqlite");
            type = "sqlite";
        }
        String prefix = str(storage, "table-prefix", "prost_");
        if (!TABLE_PREFIX.matcher(prefix).matches()) {
            warn.accept("storage.table-prefix: '" + prefix + "' skipped (letters, digits and _, up to 16) - using prost_");
            prefix = "prost_";
        }
        int port = num(mysql, "port", DEFAULT_PORT);
        if (port < 1 || port > MAX_PORT) {
            warn.accept("storage.mysql.port: " + port + " is not a port - using " + DEFAULT_PORT);
            port = DEFAULT_PORT;
        }
        Storage st = new Storage(type, str(mysql, "host", "localhost"), port, str(mysql, "database", "prost"),
                str(mysql, "user", "prost"), str(mysql, "password", ""), prefix, str(mysql, "parameters", "useSSL=false"));

        return new PluginConfig(lang, bool(language, "per-player", true), colors, List.copyOf(aliases), Set.copyOf(worlds),
                List.copyOf(areas), DimensionMode.of(dims.get("nether"), warn, "dimensions.nether"),
                DimensionMode.of(dims.get("end"), warn, "dimensions.end"), Math.max(0, num(afk, "minutes", 5)),
                bool(afk, "use-essentials", true), bool(papi, "parse-messages", true), bool(notices, "first-round", true), st,
                bool(r, "metrics", true), bool(r, "update-checker", true));
    }

    /** an empty world list = every world */
    public boolean gameWorld(String world) { return worlds.isEmpty() || worlds.contains(world); }

    private static Map<?, ?> sub(Map<?, ?> m, String key) { return m.get(key) instanceof Map<?, ?> s ? s : Map.of(); }

    private static String str(Map<?, ?> m, String key, String fallback) {
        Object v = m.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static int num(Map<?, ?> m, String key, int fallback) { return m.get(key) instanceof Number n ? n.intValue() : fallback; }

    private static boolean bool(Map<?, ?> m, String key, boolean fallback) { return m.get(key) instanceof Boolean b ? b : fallback; }
}
