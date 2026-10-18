package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.config.PluginConfig.ThemeColors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeTest {
    @Test
    void tagsBecomeColours() {
        assertEquals("<gradient:#ffd94d:#ff8f3f>Prost</gradient> <#ffd94d>x <#55e07a>y <#ff5555>z <dark_gray>w",
                Theme.DEFAULT.apply("<primary>Prost</primary> <hl>x <ok>y <err>z <muted>w"));
    }

    @Test
    void escapedTagsStayText() { assertEquals("\\<hl> \\<primary>", Theme.DEFAULT.apply("\\<hl> \\<primary>")); }

    @Test
    void anEscapedBackslashDoesNotEscapeTheTagAfterIt() {
        assertEquals("\\\\<#ffd94d> \\\\\\<hl>", Theme.DEFAULT.apply("\\\\<hl> \\\\\\<hl>"));
    }

    @Test
    void badColoursFallBack() {
        List<String> warnings = new ArrayList<>();
        Theme t = Theme.of(new ThemeColors("red", "#12", "green", "#ff0000", "nope"), warnings::add);
        assertEquals("<gradient:#ffd94d:#ff8f3f>", t.apply("<primary>"));
        assertEquals("<#ffd94d>", t.apply("<hl>"));
        assertEquals("<green>", t.apply("<ok>"));
        assertEquals("<#ff0000>", t.apply("<err>"));
        assertEquals("<dark_gray>", t.apply("<muted>"));
        assertEquals(3, warnings.size());
    }
}
