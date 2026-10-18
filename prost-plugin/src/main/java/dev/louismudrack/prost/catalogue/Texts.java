package dev.louismudrack.prost.catalogue;

import java.util.Map;

/** Catalogue texts per language ({en: ..., de: ...}): the player's language, the server's, English, then any. */
public final class Texts {
    /** the language key of a text that is the same for everyone (a rule a player typed) */
    public static final String ANY = "*";
    private static final String ENGLISH = "en";
    private static volatile String serverDefault = ENGLISH;

    private Texts() {}

    public static void serverDefault(String code) { serverDefault = code; }

    public static String pick(Map<String, String> texts, String lang) {
        for (String code : new String[]{lang, serverDefault, ENGLISH}) {
            String v = texts.get(code);
            if (v != null) return v;
        }
        return texts.isEmpty() ? "" : texts.values().iterator().next();
    }
}
