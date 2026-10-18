package dev.louismudrack.prost.config;

import dev.louismudrack.prost.config.PluginConfig.DimensionMode;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PluginConfigTest {
    private final List<String> warnings = new ArrayList<>();

    private PluginConfig parse(String yaml) { return PluginConfig.parse((Map<?, ?>) new Yaml().load(yaml), warnings::add); }

    @Test
    void defaults() {
        PluginConfig c = PluginConfig.DEFAULTS;
        assertEquals("en", c.defaultLanguage());
        assertTrue(c.perPlayerLanguage());
        assertEquals(List.of("drinks"), c.commandAliases());
        assertEquals(DimensionMode.AUTO, c.nether());
        assertEquals(5, c.afkMinutes());
        assertFalse(c.storage().mysql());
        assertEquals("prost_", c.storage().tablePrefix());
        assertTrue(c.gameWorld("any world"));
    }

    @Test
    void yamlBooleansMeanOnOff() {
        Map<?, ?> dims = (Map<?, ?>) ((Map<?, ?>) new Yaml().load("dimensions: {nether: off, end: on}")).get("dimensions");
        assertEquals(Boolean.FALSE, dims.get("nether"));
        assertEquals(Boolean.TRUE, dims.get("end"));
        PluginConfig c = parse("dimensions: {nether: off, end: on}");
        assertEquals(DimensionMode.OFF, c.nether());
        assertEquals(DimensionMode.ON, c.end());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void languageIsNormalisedOrRejected() {
        assertEquals("de", parse("language: {default: DE}").defaultLanguage());
        assertEquals("en", parse("language: {default: deutsch}").defaultLanguage());
        assertEquals(1, warnings.size());
    }

    @Test
    void aliasesAreFiltered() {
        assertEquals(List.of("trinkspiel", "drinks"),
                parse("commands: {aliases: [Trinkspiel, prost, 'bad name', drinks, drinks]}").commandAliases());
        assertEquals(2, warnings.size());
    }

    @Test
    void areasParseInAnyCornerOrder() {
        PluginConfig c = parse("excluded-areas: [{world: world, min: [10, 200, 10], max: [-10, 150, -10]}, {world: w}]");
        assertEquals(1, c.excludedAreas().size());
        AreaBox b = c.excludedAreas().get(0);
        assertTrue(b.contains("world", -10, 150, -10));
        assertTrue(b.contains("world", 10.9, 200.5, 10.9));
        assertFalse(b.contains("world", 11, 200, 10));
        assertFalse(b.contains("world_nether", 0, 160, 0));
        assertEquals(1, warnings.size());
    }

    @Test
    void storageFallsBackOnNonsense() {
        PluginConfig c = parse("storage: {type: postgres, table-prefix: 'x-y', mysql: {port: 99999}}");
        assertEquals("sqlite", c.storage().type());
        assertEquals("prost_", c.storage().tablePrefix());
        assertEquals(3306, c.storage().port());
        assertEquals(3, warnings.size());
    }

    @Test
    void worldsLimitTheGame() {
        PluginConfig c = parse("worlds: [survival]");
        assertTrue(c.gameWorld("survival"));
        assertFalse(c.gameWorld("lobby"));
    }
}
