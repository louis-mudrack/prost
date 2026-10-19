package dev.louismudrack.prost.game;

import java.util.Locale;

/** How a challenge is played. */
public enum Kind {
    TEAM("team", 2), EACH("each", 2), RACE("race", 2), DUEL("duel", 2), SOLO("solo", 1), REACTION("reaction", 2), LUCK("luck", 2), VOTE("vote", 3);

    public final String key;
    public final int minPlayers;

    Kind(String key, int minPlayers) {
        this.key = key;
        this.minPlayers = minPlayers;
    }

    public static Kind of(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (Kind kind : values()) if (kind.key.equals(k)) return kind;
        return null;
    }
}
