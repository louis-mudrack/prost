package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.config.YamlMerge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * lang/<code>.yml in the data folder: the shipped languages written and extended, every file read. What is not text
 * falls back to English with one warning per file; the jar's English stands behind the server's en.yml.
 */
public final class LangFiles {
    static final List<String> SHIPPED = List.of("en", "de");
    private static final String FOLDER = "lang";
    private static final String SUFFIX = ".yml";

    /** one file: its texts, its sections, and the keys that are neither */
    private record Parsed(String file, Map<String, Object> texts, Set<String> sections, Set<String> notText) {}

    private LangFiles() {}

    public static Lang load(JavaPlugin plugin, String defaultLanguage) {
        for (String code : SHIPPED) {
            String path = FOLDER + "/" + code + SUFFIX;
            // read() reports a file that does not parse; YamlMerge would warn about it a second time
            if (!unparseable(new File(plugin.getDataFolder(), path))) YamlMerge.ensure(plugin, path);
        }
        return read(new File(plugin.getDataFolder(), FOLDER), defaultLanguage, shippedEnglish(plugin), plugin.getLogger()::warning);
    }

    private static boolean unparseable(File f) {
        if (!f.exists()) return false;
        try {
            new YamlConfiguration().load(f);
            return false;
        } catch (IOException | InvalidConfigurationException e) {
            return true;
        }
    }

    static Lang read(File dir, String defaultLanguage, Consumer<String> warn) { return read(dir, defaultLanguage, Map.of(), warn); }

    /** shippedEnglish: what an English text falls back to when the server's en.yml lacks it, breaks it or is unreadable */
    static Lang read(File dir, String defaultLanguage, Map<String, Object> shippedEnglish, Consumer<String> warn) {
        Map<String, Parsed> parsed = new TreeMap<>();
        File[] found = dir.listFiles((d, name) -> name.endsWith(SUFFIX));
        for (File f : found == null ? new File[0] : found) {
            String file = FOLDER + "/" + f.getName();
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.load(f);
            } catch (IOException | InvalidConfigurationException e) {
                warn.accept(file + " is unreadable (" + String.valueOf(e.getMessage()).lines().findFirst().orElse("?") + ") - English is used instead");
                continue;
            }
            String code = f.getName().substring(0, f.getName().length() - SUFFIX.length()).toLowerCase(Locale.ROOT);
            parsed.put(code, parse(file, y));
        }

        Set<String> englishTexts = new HashSet<>(shippedEnglish.keySet());
        Parsed english = parsed.get(Lang.FALLBACK);
        if (english != null) englishTexts.addAll(english.texts().keySet());
        Set<String> englishSections = parents(englishTexts);

        Map<String, Map<String, Object>> files = new TreeMap<>();
        parsed.forEach((code, p) -> {
            warnMismatches(p, englishTexts, englishSections, warn);
            files.put(code, p.texts());
        });
        if (!shippedEnglish.isEmpty()) {
            Map<String, Object> en = new HashMap<>(shippedEnglish);
            en.putAll(files.getOrDefault(Lang.FALLBACK, Map.of()));
            files.put(Lang.FALLBACK, Map.copyOf(en));
        }

        String def = defaultLanguage;
        if (!files.containsKey(def) && !def.equals(Lang.FALLBACK)) {
            warn.accept("language.default '" + def + "' has no readable " + FOLDER + "/" + def + SUFFIX + " - using en");
            def = Lang.FALLBACK;
        }
        return new Lang(Map.copyOf(files), def, warn);
    }

    private static Parsed parse(String file, ConfigurationSection s) {
        Map<String, Object> texts = new HashMap<>();
        Set<String> sections = new HashSet<>(), notText = new HashSet<>();
        for (String key : s.getKeys(true)) {
            Object v = s.get(key);
            if (s.isConfigurationSection(key)) sections.add(key);
            else if (v instanceof String || (v instanceof List<?> l && l.stream().allMatch(x -> x instanceof String))) texts.put(key, v);
            else notText.add(key);
        }
        return new Parsed(file, Map.copyOf(texts), sections, notText);
    }

    /** a text turned into a section (or the other way round) leaves English to answer, like a value that is not text */
    private static void warnMismatches(Parsed p, Set<String> englishTexts, Set<String> englishSections, Consumer<String> warn) {
        Set<String> bad = new TreeSet<>(p.notText());
        for (String key : p.sections()) if (englishTexts.contains(key)) bad.add(key);
        for (String key : p.texts().keySet()) if (englishSections.contains(key)) bad.add(key);
        if (!bad.isEmpty()) warn.accept(p.file() + ": English is used for " + String.join(", ", bad)
                + " (not text, or a section where English has a text, or the other way round)");
    }

    /** "a.b.c" -> "a", "a.b" */
    private static Set<String> parents(Set<String> keys) {
        Set<String> out = new HashSet<>();
        for (String key : keys) for (int dot = key.indexOf('.'); dot > 0; dot = key.indexOf('.', dot + 1)) out.add(key.substring(0, dot));
        return out;
    }

    private static Map<String, Object> shippedEnglish(JavaPlugin plugin) {
        String path = FOLDER + "/" + Lang.FALLBACK + SUFFIX;
        InputStream in = plugin.getResource(path);
        if (in == null) throw new IllegalStateException(path + " is missing from the plugin jar");
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(reader);
            return parse(path, y).texts();
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException(path + " in the plugin jar is unreadable", e);
        }
    }
}
