package dev.louismudrack.prost.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YamlMergeTest {
    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    @Test
    void addsMissingKeysWithCommentsAndKeepsValues() throws Exception {
        YamlConfiguration user = yaml("afk:\n  minutes: 9\n");
        YamlConfiguration shipped = yaml("afk:\n  minutes: 5\n  # use EssentialsX\n  use-essentials: true\nmetrics: true\n");
        assertTrue(YamlMerge.appendMissing(user, shipped));
        assertEquals(9, user.getInt("afk.minutes"));
        assertTrue(user.getBoolean("afk.use-essentials"));
        assertTrue(user.getBoolean("metrics"));
        assertEquals(shipped.getComments("afk.use-essentials"), user.getComments("afk.use-essentials"));
    }

    @Test
    void nothingMissingChangesNothing() throws Exception {
        YamlConfiguration user = yaml("metrics: false\n");
        assertFalse(YamlMerge.appendMissing(user, yaml("metrics: true\n")));
        assertFalse(user.getBoolean("metrics"));
    }

    @Test
    void aPlainValueWhereASectionShipsIsLeftAlone() throws Exception {
        YamlConfiguration user = yaml("afk: 5\n");
        assertFalse(YamlMerge.appendMissing(user, yaml("afk:\n  minutes: 5\n")));
        assertEquals(5, user.getInt("afk"));
    }
}
