package dev.louismudrack.prost.i18n;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every shipped language has every key, with the same value tags (a translation that drops <code> is a bug). */
class LangParityTest {
    private static final Pattern TAG = Pattern.compile("<([a-z][a-z0-9_]*)>");
    private static final Set<String> NOT_VALUES = new HashSet<>(Set.of("primary", "hl", "ok", "err", "muted", "newline", "br", "reset",
            "bold", "b", "italic", "i", "em", "underlined", "u", "strikethrough", "st", "obfuscated", "obf", "gradient", "rainbow",
            "transition", "click", "hover", "key", "lang", "insertion", "font", "grey", "dark_grey"));

    static {
        NOT_VALUES.addAll(NamedTextColor.NAMES.keys());
    }

    static YamlConfiguration load(String code) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.load(new File("src/main/resources/lang/" + code + ".yml"));
        return y;
    }

    static Set<String> keys(YamlConfiguration y) {
        Set<String> out = new TreeSet<>();
        for (String k : y.getKeys(true)) if (!y.isConfigurationSection(k) && !k.startsWith("commands.")) out.add(k);
        return out;
    }

    private static Set<String> values(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = TAG.matcher(text);
        while (m.find()) if (!NOT_VALUES.contains(m.group(1))) out.add(m.group(1));
        return out;
    }

    @Test
    void germanHasExactlyTheEnglishKeys() throws Exception {
        Set<String> en = keys(load("en")), de = keys(load("de"));
        Set<String> missing = new TreeSet<>(en), extra = new TreeSet<>(de);
        missing.removeAll(de);
        extra.removeAll(en);
        assertEquals(Set.of(), missing, "missing in de.yml");
        assertEquals(Set.of(), extra, "only in de.yml");
    }

    @Test
    void bothUseTheSameValueTags() throws Exception {
        YamlConfiguration en = load("en"), de = load("de");
        for (String k : keys(en)) assertEquals(values(String.valueOf(en.get(k))), values(String.valueOf(de.get(k))), k);
    }

    @Test
    void everyValueIsAStringOrAListOfStrings() throws Exception {
        for (String code : new String[]{"en", "de"}) {
            YamlConfiguration y = load(code);
            for (String k : keys(y)) {
                Object v = y.get(k);
                boolean ok = v instanceof String || v instanceof List<?> l && l.stream().allMatch(e -> e instanceof String);
                assertTrue(ok, code + ".yml " + k + " is " + v + " - quote yes/no/on/off and numbers");
            }
        }
    }

    /** the prompt names the word that stops it: a translation that changes one must change the other */
    @Test
    void thePromptNamesACancelWord() throws Exception {
        for (String code : List.of("en", "de")) {
            YamlConfiguration y = load(code);
            List<String> words = y.getStringList("rule.cancel_words");
            assertFalse(words.isEmpty(), code);
            assertTrue(y.getString("rule.type_prompt").contains("\"" + words.get(0) + "\""), code);
        }
    }
}
