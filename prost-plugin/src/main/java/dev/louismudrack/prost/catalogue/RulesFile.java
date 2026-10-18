package dev.louismudrack.prost.catalogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** rules.yml: a list of {language: text}; items without any text are skipped. */
public final class RulesFile {
    private RulesFile() {}

    public static List<Rule> parse(Object root) {
        List<Rule> out = new ArrayList<>();
        if (!(root instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Map<String, String> texts = new LinkedHashMap<>();
            m.forEach((k, v) -> { if (v != null && !String.valueOf(v).isBlank()) texts.put(String.valueOf(k).toLowerCase(Locale.ROOT), String.valueOf(v)); });
            if (!texts.isEmpty()) out.add(new Rule(texts));
        }
        return out;
    }
}
