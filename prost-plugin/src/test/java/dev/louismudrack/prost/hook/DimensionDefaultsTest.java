package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.config.PluginConfig;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static dev.louismudrack.prost.config.PluginConfig.DimensionMode.*;
import static org.junit.jupiter.api.Assertions.*;

class DimensionDefaultsTest {
    @Test
    void autoNeedsALoadedWorldAndThePermission() {
        assertTrue(DimensionDefaults.decide(AUTO, true, true));
        assertFalse(DimensionDefaults.decide(AUTO, false, true));
        assertFalse(DimensionDefaults.decide(AUTO, true, false));
    }

    @Test
    void onAndOffOverride() {
        assertTrue(DimensionDefaults.decide(ON, false, false));
        assertFalse(DimensionDefaults.decide(OFF, true, true));
    }

    @Test
    void theOverworldIsAlwaysOpen() {
        DimensionDefaults d = new DimensionDefaults(() -> PluginConfig.DEFAULTS, env -> false, () -> false, () -> false);
        assertTrue(d.isOpen(World.Environment.NORMAL));
        assertFalse(d.isOpen(World.Environment.NETHER));
        assertFalse(d.isOpen(World.Environment.THE_END));
    }
}
