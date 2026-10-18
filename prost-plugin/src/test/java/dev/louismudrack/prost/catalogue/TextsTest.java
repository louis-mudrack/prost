package dev.louismudrack.prost.catalogue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextsTest {
    @AfterEach
    void reset() { Texts.serverDefault("en"); }

    @Test
    void thePlayersLanguageThenTheServersThenEnglishThenAny() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("fr", "Fièvre");
        t.put("en", "Iron fever");
        t.put("de", "Eisenfieber");
        assertEquals("Eisenfieber", Texts.pick(t, "de"));
        assertEquals("Iron fever", Texts.pick(t, "pl"));
        Texts.serverDefault("fr");
        assertEquals("Fièvre", Texts.pick(t, "pl"));
        assertEquals("only", Texts.pick(Map.of("it", "only"), "de"));
        assertEquals("", Texts.pick(Map.of(), "de"));
    }

    @Test
    void aTypedRuleReadsTheSameForEveryone() {
        Rule r = Rule.custom("Jeder Satz endet mit Diggi");
        assertEquals("Jeder Satz endet mit Diggi", r.text("en"));
        assertEquals("Jeder Satz endet mit Diggi", r.text("de"));
    }
}
