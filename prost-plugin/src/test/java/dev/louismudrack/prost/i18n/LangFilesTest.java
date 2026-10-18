package dev.louismudrack.prost.i18n;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LangFilesTest {
    @TempDir Path dir;
    private final List<String> warnings = new ArrayList<>();

    private void write(String name, String text) throws Exception { Files.writeString(dir.resolve(name), text, StandardCharsets.UTF_8); }

    private Lang read(String defaultLanguage) { return LangFiles.read(dir.toFile(), defaultLanguage, warnings::add); }

    @Test
    void readsEveryFileFlat() throws Exception {
        write("en.yml", "a:\n  b: Hello\nlist: [x, y]\n");
        write("de.yml", "a:\n  b: Hallo\n");
        Lang lang = read("de");
        assertEquals("Hallo", lang.template("de", "a.b"));
        assertEquals(List.of("x", "y"), lang.lines("de", "list"));
        assertEquals(Set.of("en", "de"), lang.languages());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void aBrokenFileIsSkippedWithOneWarning() throws Exception {
        write("en.yml", "a: A\n");
        write("de.yml", "a: [unclosed\n");
        Lang lang = read("en");
        assertFalse(lang.has("de"));
        assertEquals("A", lang.template("de", "a"));
        assertEquals(1, warnings.size());
    }

    @Test
    void valuesThatAreNoTextFallBackToEnglish() throws Exception {
        write("en.yml", "a: A\nb:\n  c: C\n");
        write("de.yml", "a: 5\nb: plain\n");
        Lang lang = read("en");
        assertEquals("A", lang.template("de", "a"));
        assertEquals("C", lang.template("de", "b.c"));
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void severalBadKeysInOneFileGiveOneWarningNamingThemAll() throws Exception {
        write("en.yml", "a: A\nb: B\nc: C\n");
        write("de.yml", "a:\n  x: turned into a section\nb: 5\nc: [1, 2]\n");
        Lang lang = read("en");
        assertEquals("A", lang.template("de", "a"));
        assertEquals("B", lang.template("de", "b"));
        assertEquals("C", lang.template("de", "c"));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("de.yml") && warnings.get(0).contains("a, b, c"), warnings.get(0));
    }

    @Test
    void anUnknownServerLanguageFallsBackToEnglish() throws Exception {
        write("en.yml", "a: A\n");
        assertEquals("en", read("fr").defaultLanguage());
        assertEquals(1, warnings.size());
    }

    @Test
    void aBrokenEnglishFileFallsBackToTheShippedEnglish() throws Exception {
        write("en.yml", "a: [unclosed\n");
        Lang lang = LangFiles.read(dir.toFile(), "en", Map.of("a", "A-shipped"), warnings::add);
        assertTrue(lang.has("en"));
        assertEquals("A-shipped", lang.template("en", "a"));
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void theAdminsEnglishWinsAndItsBadKeysUseTheShippedText() throws Exception {
        write("en.yml", "a: Mine\nb: 5\n");
        Lang lang = LangFiles.read(dir.toFile(), "en", Map.of("a", "A-shipped", "b", "B-shipped"), warnings::add);
        assertEquals("Mine", lang.template("en", "a"));
        assertEquals("B-shipped", lang.template("en", "b"));
        assertEquals(1, warnings.size(), warnings.toString());
    }
}
