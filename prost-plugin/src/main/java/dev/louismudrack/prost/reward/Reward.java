package dev.louismudrack.prost.reward;

import java.util.Locale;

/** What winning a challenge can give - real life only. */
public enum Reward {
    HANDOUT("handout"), RULE("rule"), JOKER("joker"), NEXT("next");

    final String key;

    Reward(String key) { this.key = key; }

    public static Reward of(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (Reward r : values()) if (r.key.equals(k)) return r;
        return null;
    }
}
