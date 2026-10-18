package dev.louismudrack.prost;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** challenges.yml -> the entries that pass CatalogueRules against the running server; the rest is logged and skipped. */
final class Catalogue {
    static final CatalogueRules.Names BUKKIT = new CatalogueRules.Names() {
        public boolean material(String n) { return Material.getMaterial(n) != null; }

        public boolean entity(String n) {
            try { EntityType.valueOf(n); return true; } catch (IllegalArgumentException e) { return false; }
        }

        public boolean statistic(String n) {
            try { Statistic.valueOf(n); return true; } catch (IllegalArgumentException e) { return false; }
        }

        public boolean item(String n) { Material m = Material.getMaterial(n); return m != null && m.isItem(); }

        public boolean block(String n) { Material m = Material.getMaterial(n); return m != null && m.isBlock(); }
    };

    private Catalogue() {}

    static List<ChallengeDef> load(Object yaml, Logger log) {
        CatalogueParser.Parsed parsed = CatalogueParser.parse(yaml instanceof Map<?, ?> m ? m : Map.of());
        for (String problem : parsed.problems()) log.warning("challenges.yml: " + problem + " - skipped");
        List<ChallengeDef> ok = new ArrayList<>();
        for (ChallengeDef d : parsed.defs()) {
            List<String> problems = CatalogueRules.validate(d, BUKKIT);
            if (problems.isEmpty()) ok.add(d);
            else log.warning("challenges.yml: " + d.id() + ": " + String.join("; ", problems) + " - skipped");
        }
        return List.copyOf(ok);
    }

    static ChallengeDef find(List<ChallengeDef> all, String id) {
        for (ChallengeDef d : all) if (d.id().equalsIgnoreCase(id)) return d;
        return null;
    }
}
