package dev.louismudrack.prost.catalogue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A real-life table rule in any languages ({en: ..., de: ...}); a typed rule reads the same for everyone. */
public record Rule(Map<String, String> texts) {
    public Rule {
        texts = Collections.unmodifiableMap(new LinkedHashMap<>(texts));
    }

    public static Rule custom(String text) { return new Rule(Map.of(Texts.ANY, text)); }

    public String text(String lang) { return Texts.pick(texts, lang); }
}
