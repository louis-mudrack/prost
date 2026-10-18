package dev.louismudrack.prost.i18n;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LangTest {
    @Test
    void theLanguageThenTheServerDefaultThenEnglish() {
        Lang lang = new Lang(Map.of("en", Map.of("a", "A-en", "b", "B-en", "c", "C-en"), "de", Map.of("a", "A-de"),
                "fr", Map.of("b", "B-fr")), "fr", w -> { });
        assertEquals("A-de", lang.template("de", "a"));
        assertEquals("B-fr", lang.template("de", "b"));
        assertEquals("C-en", lang.template("de", "c"));
    }

    @Test
    void aKeyMissingEverywhereShowsItselfAndWarnsOnce() {
        List<String> warnings = new ArrayList<>();
        Lang lang = new Lang(Map.of("en", Map.of()), "en", warnings::add);
        assertEquals("x.y", lang.template("en", "x.y"));
        lang.template("en", "x.y");
        assertEquals(1, warnings.size());
    }

    @Test
    void listsAreLinesOrJoinedForChat() {
        Lang lang = new Lang(Map.of("en", Map.of("lore", List.of("one", "two"), "single", "s")), "en", w -> { });
        assertEquals(List.of("one", "two"), lang.lines("en", "lore"));
        assertEquals("one<newline>two", lang.template("en", "lore"));
        assertEquals(List.of("s"), lang.lines("en", "single"));
    }

    @Test
    void aliasesPerLanguage() {
        Lang lang = new Lang(Map.of("en", Map.of(), "de", Map.of("commands.invite", List.of("einladen"))), "en", w -> { });
        assertEquals(Map.of("invite", List.of("einladen")), lang.aliases("de"));
        assertEquals(Map.of(), lang.aliases("en"));
    }
}
