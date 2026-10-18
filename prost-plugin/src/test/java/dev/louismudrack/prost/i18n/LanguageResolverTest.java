package dev.louismudrack.prost.i18n;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static dev.louismudrack.prost.i18n.LanguageResolver.choose;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageResolverTest {
    private final Predicate<String> available = Set.of("en", "de")::contains;
    private final Optional<String> none = Optional.empty();

    @Test
    void aProviderWins() { assertEquals("de", choose(Optional.of("DE"), Optional.of("en"), "en", true, "en", available)); }

    @Test
    void storedThenClientThenDefault() {
        assertEquals("de", choose(none, Optional.of("de"), "en", true, "en", available));
        assertEquals("de", choose(none, none, "de_AT", true, "en", available));
        assertEquals("en", choose(none, none, "fr", true, "en", available));
    }

    @Test
    void unknownOrOddCodesAreIgnoredOrNormalised() {
        assertEquals("en", choose(Optional.of("xx"), Optional.of("yy"), "zz", true, "en", available));
        assertEquals("de", choose(Optional.of("DE-at"), none, "en", true, "en", available));
        assertEquals("de", choose(Optional.of(" "), Optional.of(" de "), "en", true, "en", available));
    }

    @Test
    void perPlayerOffUsesTheServerLanguageButAProviderStillWins() {
        assertEquals("de", choose(none, Optional.of("en"), "en", false, "de", available));
        assertEquals("en", choose(Optional.of("en"), none, "de", false, "de", available));
    }
}
