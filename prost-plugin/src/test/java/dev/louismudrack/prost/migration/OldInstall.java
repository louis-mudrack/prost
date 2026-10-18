package dev.louismudrack.prost.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** An AniDrinks 0.2.0 folder: its schema, two players, one finished round. */
public final class OldInstall {
    public static final UUID KASHEYY = UUID.fromString("0e14c9a2-bd52-402a-a715-302d6c0bf0b5");
    public static final UUID ALEX = UUID.fromString("a8cee66a-0f13-4058-9b1b-2d3190e07922");

    private OldInstall() {}

    /** plugins/AniDrinks with its files and stats.db; returns the folder */
    public static Path write(Path pluginsDir) throws Exception {
        Path old = Files.createDirectories(pluginsDir.resolve("AniDrinks"));
        Files.writeString(old.resolve("drinks.yml"), "max-players: 6\n");
        Files.writeString(old.resolve("challenges.yml"), "mine_iron: {}\n");
        Files.writeString(old.resolve("rules.yml"), "- {de: a, en: b}\n");
        Files.writeString(old.resolve("rounds.yml"), "[]\n");
        sql(old.resolve("stats.db"),
                "CREATE TABLE stats (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, sips_drunk INTEGER NOT NULL DEFAULT 0, "
                        + "sips_water INTEGER NOT NULL DEFAULT 0, sips_given INTEGER NOT NULL DEFAULT 0, won INTEGER NOT NULL DEFAULT 0, "
                        + "lost INTEGER NOT NULL DEFAULT 0, roulette_hits INTEGER NOT NULL DEFAULT 0, jokers_used INTEGER NOT NULL DEFAULT 0, "
                        + "rules_made INTEGER NOT NULL DEFAULT 0, rounds INTEGER NOT NULL DEFAULT 0, last_played INTEGER NOT NULL DEFAULT 0)",
                "CREATE TABLE rounds (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL, host TEXT NOT NULL, "
                        + "started INTEGER NOT NULL, ended INTEGER NOT NULL, members INTEGER NOT NULL, challenges INTEGER NOT NULL)",
                // a different value in every counter column, so a swapped pair shows
                "INSERT INTO stats VALUES ('" + KASHEYY + "', 'Kasheyy', 26, 4, 12, 7, 3, 6, 1, 5, 2, 1792283176874)",
                "INSERT INTO stats VALUES ('" + ALEX + "', 'Alex', 27, 9, 10, 8, 15, 13, 11, 14, 3, 1792283176875)",
                "INSERT INTO rounds (code, host, started, ended, members, challenges) VALUES ('L9D5', '" + KASHEYY
                        + "', 1792271070891, 1792283176875, 2, 16)");
        return old;
    }

    /** AniDrinks' rounds.yml with a running round: Kasheyy on water with a limit of 12, one rule */
    public static void runningRound(Path old) throws IOException {
        Files.writeString(old.resolve("rounds.yml"), "- code: L9D5\n  host: '" + KASHEYY + "'\n  pace: normal\n  created: 1792271070891\n"
                + "  rules: [{de: Nur links trinken, en: Drink left-handed}]\n"
                + "  members:\n    '" + KASHEYY + "': {name: Kasheyy, limit: 12, water: true}\n");
    }

    /** the AniDrinks rounds table without its columns: reading it fails after the stats are in */
    public static void breakRounds(Path old) throws SQLException {
        sql(old.resolve("stats.db"), "ALTER TABLE rounds RENAME TO rounds_kept", "CREATE TABLE rounds (id INTEGER PRIMARY KEY, code TEXT)");
    }

    public static void repairRounds(Path old) throws SQLException {
        sql(old.resolve("stats.db"), "DROP TABLE rounds", "ALTER TABLE rounds_kept RENAME TO rounds");
    }

    public static void sql(Path db, String... statements) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db); Statement st = c.createStatement()) {
            for (String s : statements) st.executeUpdate(s);
        }
    }
}
