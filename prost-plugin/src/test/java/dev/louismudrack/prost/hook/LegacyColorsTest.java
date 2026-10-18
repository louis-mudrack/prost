package dev.louismudrack.prost.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Placeholders from other plugins (LuckPerms prefixes...) return § colour codes, which MiniMessage cannot read. */
class LegacyColorsTest {
    @Test
    void codesBecomeTags() { assertEquals("<red>Red <bold>Bold<reset>", LegacyColors.toMiniMessage("§cRed §lBold§r")); }

    @Test
    void ampersandCodesToo() { assertEquals("<red>Red", LegacyColors.toMiniMessage("&cRed")); }

    @Test
    void hexBecomesAHexTag() { assertEquals("<#ff0000>X", LegacyColors.toMiniMessage("§x§f§f§0§0§0§0X")); }

    @Test
    void plainTextStays() { assertEquals("100% plain", LegacyColors.toMiniMessage("100% plain")); }
}
