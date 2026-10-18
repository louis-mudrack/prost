package dev.louismudrack.prost;

import java.util.Locale;

/** How a challenge is played (spec §4.2). */
enum Kind {
    TEAM("team", 2), EACH("each", 2), RACE("race", 2), DUEL("duel", 2), SOLO("solo", 1);

    final String key;
    final int minPlayers;

    Kind(String key, int minPlayers) {
        this.key = key;
        this.minPlayers = minPlayers;
    }

    static Kind of(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (Kind kind : values()) if (kind.key.equals(k)) return kind;
        return null;
    }
}
