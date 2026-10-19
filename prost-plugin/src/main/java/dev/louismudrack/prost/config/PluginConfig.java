package dev.louismudrack.prost.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** config.yml: general settings. Missing or invalid values fall back to the defaults with one warning each. */
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
        Section r = new Section(root == null ? Map.of() : root, "", warn);
        Section language = r.sub("language"), theme = r.sub("theme"), commands = r.sub("commands"), dims = r.sub("dimensions"),
                afk = r.sub("afk"), papi = r.sub("placeholderapi"), notices = r.sub("notices"), storage = r.sub("storage"),
                mysql = storage.sub("mysql");

        String lang = language.str("default", "en").trim().toLowerCase(Locale.ROOT);
        if (!LANGUAGE.matcher(lang).matches()) {
            warn.accept("language.default: '" + lang + "' is not a language code like en or de - using en");
            lang = "en";
        }
        ThemeColors colors = new ThemeColors(theme.str("primary", "#ffd94d:#ff8f3f"), theme.str("highlight", "#ffd94d"),
                theme.str("ok", "#55e07a"), theme.str("error", "#ff5555"), theme.str("muted", "dark_gray"));

        List<String> aliases = new ArrayList<>();
        for (Object o : commands.list("aliases", List.of("drinks"))) {
            String a = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
            if (!ALIAS.matcher(a).matches() || a.equals(MAIN_COMMAND)) {
                warn.accept("commands.aliases: '" + o + "' skipped (letters, digits, _ and - only; not 'prost')");
                continue;
            }
            if (!aliases.contains(a)) aliases.add(a);
        }

        Set<String> worlds = new LinkedHashSet<>();
        for (Object o : r.list("worlds", List.of())) worlds.add(String.valueOf(o));

        List<AreaBox> areas = new ArrayList<>();
        for (Object o : r.list("excluded-areas", List.of())) {
            AreaBox b = AreaBox.parse(o);
            if (b == null) warn.accept("excluded-areas: " + o + " skipped (needs world, min: [x, y, z], max: [x, y, z])");
            else areas.add(b);
        }

        String type = storage.str("type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if (!type.equals("sqlite") && !type.equals("mysql")) {
            warn.accept("storage.type: '" + type + "' is not sqlite or mysql - using sqlite");
            type = "sqlite";
        }
        String prefix = storage.str("table-prefix", "prost_");
        if (!TABLE_PREFIX.matcher(prefix).matches()) {
            warn.accept("storage.table-prefix: '" + prefix + "' skipped (letters, digits and _, up to 16) - using prost_");
            prefix = "prost_";
        }
        int port = mysql.num("port", DEFAULT_PORT);
        if (port < 1 || port > MAX_PORT) {
            warn.accept("storage.mysql.port: " + port + " is not a port - using " + DEFAULT_PORT);
            port = DEFAULT_PORT;
        }
        Storage st = new Storage(type, mysql.str("host", "localhost"), port, mysql.str("database", "prost"),
                mysql.str("user", "prost"), mysql.str("password", ""), prefix, mysql.str("parameters", "useSSL=false"));

        return new PluginConfig(lang, language.bool("per-player", true), colors, List.copyOf(aliases), Set.copyOf(worlds),
                List.copyOf(areas), DimensionMode.of(dims.get("nether"), warn, "dimensions.nether"),
                DimensionMode.of(dims.get("end"), warn, "dimensions.end"), Math.max(0, afk.num("minutes", 5)),
                afk.bool("use-essentials", true), papi.bool("parse-messages", true), notices.bool("first-round", true), st,
                r.bool("metrics", true), r.bool("update-checker", true));
    }

    /** an empty world list = every world */
    public boolean gameWorld(String world) { return worlds.isEmpty() || worlds.contains(world); }

    /**
     * One level of the file. A value of the wrong type warns once: a single value where a list belongs is a one-element
     * list, a number or true/false in quotes is that number or boolean, anything else is the default.
     */
    private record Section(Map<?, ?> values, String path, Consumer<String> warn) {
        private static final Pattern INTEGER = Pattern.compile("[+-]?\\d{1,9}");

        Object get(String key) { return values.get(key); }

        Section sub(String key) { return new Section(values.get(key) instanceof Map<?, ?> m ? m : Map.of(), name(key), warn); }

        String str(String key, String fallback) {
            Object v = values.get(key);
            return v == null ? fallback : String.valueOf(v);
        }

        int num(String key, int fallback) {
            Object v = values.get(key);
            if (v == null) return fallback;
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s && INTEGER.matcher(s.trim()).matches()) {
                int n = Integer.parseInt(s.trim());
                warn.accept(name(key) + ": '" + v + "' is text, not a number - read as " + n + " (write it without quotes)");
                return n;
            }
            warn.accept(name(key) + ": '" + v + "' is not a number - using " + fallback);
            return fallback;
        }

        boolean bool(String key, boolean fallback) {
            Object v = values.get(key);
            if (v == null) return fallback;
            if (v instanceof Boolean b) return b;
            if (v instanceof String s && (s.trim().equalsIgnoreCase("true") || s.trim().equalsIgnoreCase("false"))) {
                boolean b = Boolean.parseBoolean(s.trim());
                warn.accept(name(key) + ": '" + v + "' is text, not true or false - read as " + b + " (write it without quotes)");
                return b;
            }
            warn.accept(name(key) + ": '" + v + "' is not true or false - using " + fallback);
            return fallback;
        }

        /** a key without a value is an empty list */
        List<?> list(String key, List<?> fallback) {
            if (!values.containsKey(key)) return fallback;
            Object v = values.get(key);
            if (v == null) return List.of();
            if (v instanceof List<?> l) return l;
            if (v instanceof Map) {
                warn.accept(name(key) + ": a section where a list belongs - using " + fallback);
                return fallback;
            }
            warn.accept(name(key) + ": '" + v + "' is one value, not a list - read as [" + v + "]");
            return List.of(v);
        }

        private String name(String key) { return path.isEmpty() ? key : path + "." + key; }
    }
}
