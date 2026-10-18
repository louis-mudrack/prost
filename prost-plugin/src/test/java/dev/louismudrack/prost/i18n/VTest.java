package dev.louismudrack.prost.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VTest {
    @Test
    void anEscapedTagStaysLiteral() { assertEquals("\\<player>", V.fill("\\<player>", V.of("player", "X"))); }

    @Test
    void anEvenRunOfBackslashesIsNotAnEscape() { assertEquals("\\\\X", V.fill("\\\\<player>", V.of("player", "X"))); }

    @Test
    void fillsNamedValues() { assertEquals("Round <hl>ABCD opened", V.fill("Round <hl><code> opened", V.of("code", "ABCD"))); }

    @Test
    void tagsThatAreNoValuesStay() { assertEquals("<white>x <gray>", V.fill("<white>x <gray>", V.of("code", 1))); }

    @Test
    void valuesAreEscaped() { assertEquals("Rule: \\<red>hi", V.fill("Rule: <rule>", V.of("rule", "<red>hi"))); }

    @Test
    void singlePassSubstitution() { assertEquals("A=\\<b> B=2", V.fill("A=<a> B=<b>", V.of("a", "<b>"), V.of("b", 2))); }

    @Test
    void markupValuesGoInAsTheyAre() { assertEquals("x <hl>y", V.fill("x <v>", V.mm("v", "<hl>y"))); }

    @Test
    void themeTagsAndBackslashesInAValueAreEscapedToo() {
        assertEquals("\\<hl>x\\</hl> \\<primary> \\\\", V.fill("<v>", V.of("v", "<hl>x</hl> <primary> \\")));
    }
}
