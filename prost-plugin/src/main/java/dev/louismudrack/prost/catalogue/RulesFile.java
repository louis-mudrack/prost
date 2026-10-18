package dev.louismudrack.prost.catalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** rules.yml: a list of {de, en}; incomplete items are skipped. */
public final class RulesFile {
    private RulesFile() {}

    public static List<Rule> parse(Object root) {
        List<Rule> out = new ArrayList<>();
        if (!(root instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object de = m.get("de"), en = m.get("en");
            if (de == null || en == null || String.valueOf(de).isBlank() || String.valueOf(en).isBlank()) continue;
            out.add(new Rule(String.valueOf(de), String.valueOf(en)));
        }
        return out;
    }
}
