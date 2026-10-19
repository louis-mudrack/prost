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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static dev.louismudrack.prost.Checks.*;

/**
 * The SHIPPED challenges.yml and rules.yml against the Paper API on the test classpath. Enum names are looked up by
 * reflection WITHOUT initialising the enum classes (Material/EntityType must not need a running server here).
 */
class ShippedCatalogueTest {
    private static final Path CATALOGUE = Path.of("src/main/resources/challenges.yml");
    /** spec §8: the entries Prost 0.4.0 added */
    private static final Set<String> ADDED_IN_040 = Set.of("react_crouch", "react_water", "react_chat", "react_look_up", "react_jump",
            "react_dirt", "react_sit", "react_eat", "react_freeze", "hot_potato", "wheel", "coin_flip", "three_chests", "dice",
            "high_low", "mystery_box", "vote_ugly_house", "vote_statue", "vote_pixel_art", "vote_armor_stand", "vote_garden",
            "vote_portrait", "vote_outfit", "vote_sign", "vote_mini_house", "vote_bridge");

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(options);
    }

    private static Map<?, ?> catalogue() throws Exception {
        try (Reader r = Files.newBufferedReader(CATALOGUE)) { return yaml().load(r); }
    }

    /** CatalogueMerge appends shipped entries to an admin's file as lines of text: each line must be a whole entry */
    @Test
    void everyEntryIsOneLine() throws Exception {
        Map<?, ?> root = catalogue();
        List<String> entries = Files.readAllLines(CATALOGUE).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        eq("one line per entry", root.size(), entries.size());
        for (String line : entries) {
            Map<?, ?> alone = yaml().load(line);
            eq(line + ": one entry", 1, alone.size());
            Object id = alone.keySet().iterator().next();
            eq(id + ": the line alone reads as in the file", root.get(id), alone.get(id));
        }
    }

    @Test
    void theEntriesOf040SayWhenTheyCame() throws Exception {
        Map<String, Object> since = new LinkedHashMap<>();
        catalogue().forEach((id, entry) -> since.put(String.valueOf(id), ((Map<?, ?>) entry).get("since")));
        Set<String> added = new TreeSet<>(), older = new TreeSet<>();
        since.forEach((id, version) -> (version == null ? older : added).add(id));
        eq("the 26 new entries", new TreeSet<>(ADDED_IN_040), added);
        eq("the 103 older entries carry no since", 103, older.size());
        for (String id : added) eq(id + ": since", "0.4.0", since.get(id));
    }

    @Test
    void sinceIsNoPartOfTheChallenge() {
        Map<String, Object> entry = new LinkedHashMap<>(Map.of("name", Map.of("en", "Hop"), "text", Map.of("en", "Jump"),
                "tracker", "reaction", "mode", "jump", "seconds", 15, "sips", 1, "kinds", List.of("reaction"), "rewards", List.of("handout")));
        ChallengeDef without = CatalogueParser.entry("hop", entry);
        entry.put("since", "0.4.0");
        eq("since changes nothing", without, CatalogueParser.entry("hop", entry));
    }

    @Test
    void shippedCatalogue() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Map<?, ?> root;
        try (Reader r = Files.newBufferedReader(Path.of("src/main/resources/challenges.yml"))) { root = new Yaml(options).load(r); }
        CatalogueParser.Parsed parsed = CatalogueParser.parse(root);
        eq("every entry parses", List.of(), parsed.problems());
        eq("the catalogue ships 129 challenges", 129, parsed.defs().size());
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
        eq("all eight kinds playable", EnumSet.allOf(Kind.class), kinds);
        eq("eight kinds", 8, kinds.size());
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
