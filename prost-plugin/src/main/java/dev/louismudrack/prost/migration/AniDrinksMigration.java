package dev.louismudrack.prost.migration;

import dev.louismudrack.prost.game.Rounds;
import dev.louismudrack.prost.storage.StatsRow;
import dev.louismudrack.prost.storage.StatsStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Takes over an AniDrinks installation (the plugin Prost grew out of): the admin-owned files, the running rounds and all
 * stats. Every step may run twice: files are copied only where Prost has none, stats are written with absolute values,
 * a round once per (code, start). Renaming the old folder is the last step, after every write is confirmed, so a failure
 * or a crash in between leaves plugins/AniDrinks as it was for the next try.
 */
public final class AniDrinksMigration {
    public static final String SOURCE = "AniDrinks";
    private static final String DONE_SUFFIX = ".migrated";
    private static final long WAIT_SECONDS = 60;

    /** NOTHING: no plugins/AniDrinks; BLOCKED: AniDrinks still installed, or Prost stats at the start; FAILED: see the log */
    public enum Outcome { DONE, NOTHING, BLOCKED, FAILED }

    public record Result(int players, int rounds, List<String> files, String renamedTo) {}

    private record Copy(String from, String to) {}

    private static final List<Copy> FILES = List.of(new Copy("challenges.yml", "challenges.yml"), new Copy("rules.yml", "rules.yml"),
            new Copy("drinks.yml", "game.yml"), new Copy("rounds.yml", Rounds.FILE));

    private final File pluginsDir, prostDir;
    private final StatsStore store;
    private final Logger log;

    public AniDrinksMigration(File pluginsDir, File prostDir, StatsStore store, Logger log) {
        this.pluginsDir = pluginsDir;
        this.prostDir = prostDir;
        this.store = store;
        this.log = log;
    }

    public boolean found() { return source().isDirectory(); }

    /** the start's guard (spec §9): Prost's own stats, or those of an earlier take-over, are never replaced unasked */
    public boolean prostHasStats() throws SQLException { return await(store.hasData()); }

    /** null when there is nothing to take over */
    public Result run() throws IOException, SQLException {
        File source = source();
        if (!source.isDirectory()) return null;
        Files.createDirectories(prostDir.toPath());
        List<String> files = new ArrayList<>(), kept = new ArrayList<>();
        copyFiles(source, files, kept);
        int players = 0, rounds = 0;
        File db = new File(source, "stats.db");
        if (db.isFile()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("sqlite-jdbc is missing", e);
            }
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath())) {
                players = importStats(c);
                rounds = importRounds(c);
            }
        }
        File target = new File(pluginsDir, SOURCE + DONE_SUFFIX);
        if (target.exists()) target = new File(pluginsDir, SOURCE + DONE_SUFFIX + "-" + System.currentTimeMillis());
        Files.move(source.toPath(), target.toPath());
        log.info("AniDrinks data taken over: " + players + " players, " + rounds + " finished rounds, files " + files
                + (kept.isEmpty() ? "" : ", Prost's own kept " + kept) + " - the old folder is now " + target.getName());
        return new Result(players, rounds, List.copyOf(files), target.getName());
    }

    private File source() { return new File(pluginsDir, SOURCE); }

    /** a file Prost already has stays: an earlier, interrupted take-over copied it, or the admin wrote it */
    private void copyFiles(File source, List<String> copied, List<String> kept) throws IOException {
        for (Copy f : FILES) {
            Path src = new File(source, f.from()).toPath(), dst = new File(prostDir, f.to()).toPath();
            if (!Files.isRegularFile(src)) continue;
            if (Files.exists(dst)) {
                kept.add(f.to());
                continue;
            }
            Files.copy(src, dst);
            copied.add(f.to());
        }
    }

    private int importStats(Connection c) throws SQLException {
        int n = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM stats")) {
            while (rs.next()) {
                StatsRow row = new StatsRow(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getInt("sips_drunk"),
                        rs.getInt("sips_water"), rs.getInt("sips_given"), rs.getInt("won"), rs.getInt("lost"), rs.getInt("roulette_hits"),
                        rs.getInt("jokers_used"), rs.getInt("rules_made"), rs.getInt("rounds"));
                await(store.importRow(row, rs.getLong("last_played")));
                n++;
            }
        }
        return n;
    }

    private int importRounds(Connection c) throws SQLException {
        int n = 0;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT code, host, started, ended, members, challenges FROM rounds")) {
            while (rs.next()) {
                await(store.importRound(rs.getString("code"), rs.getString("host"), rs.getLong("started"), rs.getLong("ended"),
                        rs.getInt("members"), rs.getInt("challenges")));
                n++;
            }
        }
        return n;
    }

    private static <T> T await(CompletableFuture<T> f) throws SQLException {
        try {
            return f.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("interrupted while taking over the AniDrinks data", e);
        } catch (ExecutionException e) {
            throw new SQLException("Prost's stats database: " + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            throw new SQLException("Prost's stats database did not answer within " + WAIT_SECONDS + " s", e);
        }
    }
}
