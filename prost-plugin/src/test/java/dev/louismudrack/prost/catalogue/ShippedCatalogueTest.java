package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.game.Kind;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.louismudrack.prost.Checks.*;

/**
 * The SHIPPED challenges.yml and rules.yml against the Paper API on the test classpath. Enum names are looked up by
 * reflection WITHOUT initialising the enum classes (Material/EntityType must not need a running server here).
 */
class ShippedCatalogueTest {
    @Test
    void shippedCatalogue() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Map<?, ?> root;
        try (Reader r = Files.newBufferedReader(Path.of("src/main/resources/challenges.yml"))) { root = new Yaml(options).load(r); }
        CatalogueParser.Parsed parsed = CatalogueParser.parse(root);
        eq("every entry parses", List.of(), parsed.problems());
        eq("the catalogue ships 119 challenges", 119, parsed.defs().size());
        CatalogueRules.Names api = new CatalogueRules.Names() {
            public boolean material(String n) { return field("org.bukkit.Material", n); }
            public boolean entity(String n) { return field("org.bukkit.entity.EntityType", n); }
            public boolean statistic(String n) { return field("org.bukkit.Statistic", n); }
            // isItem()/isBlock() need a running server (registries): checked when the server loads the file (Catalogue.BUKKIT)
            public boolean item(String n) { return true; }
            public boolean block(String n) { return true; }
        };
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        Set<String> trackers = new HashSet<>();
        for (ChallengeDef d : parsed.defs()) {
            for (String problem : CatalogueRules.validate(d, api)) check(d.id() + ": " + problem, false);
            check(d.id() + ": name and text in English and German",
                    d.names().containsKey("en") && d.names().containsKey("de") && d.texts().containsKey("en") && d.texts().containsKey("de"));
            kinds.addAll(d.kinds());
            trackers.add(d.tracker());
        }
        eq("every kind is playable", EnumSet.allOf(Kind.class), kinds);
        eq("every delivery-1 tracker is used", CatalogueRules.TRACKERS, trackers);
        check("Nether challenges exist (gate test material)", parsed.defs().stream().anyMatch(d -> d.needs().contains(Need.NETHER)));
        check("End challenges exist", parsed.defs().stream().anyMatch(d -> d.needs().contains(Need.END)));
        check("enough solo-capable challenges for a solo round (>= 60)",
                parsed.defs().stream().filter(d -> d.kinds().contains(Kind.SOLO)).count() >= 60);

        Object rulesRoot;
        try (Reader r = Files.newBufferedReader(Path.of("src/main/resources/rules.yml"))) { rulesRoot = new Yaml(options).load(r); }
        eq("30 prepared rules", 30, RulesFile.parse(rulesRoot).size());
    }

    static boolean field(String cls, String name) {
        try {
            Class.forName(cls, false, ShippedCatalogueTest.class.getClassLoader()).getField(name);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
