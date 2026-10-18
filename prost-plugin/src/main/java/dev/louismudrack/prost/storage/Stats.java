package dev.louismudrack.prost.storage;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Round;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * All-time numbers per player (spec §12.3) in plugins/ProstPlugin/stats.db - SQLite, survival-local (no shared DB, no
 * password). One background thread owns the connection, so reads and writes never race; close() drains it inline.
 */
public final class Stats {
    public enum Field {
        DRUNK("sips_drunk"), WATER("sips_water"), GIVEN("sips_given"), WON("won"), LOST("lost"), ROULETTE("roulette_hits"),
        JOKERS("jokers_used"), RULES("rules_made"), ROUNDS("rounds");

        final String column;

        Field(String column) { this.column = column; }
    }

    public record Row(UUID id, String name, int drunk, int water, int given, int won, int lost, int roulette, int jokers, int rules, int rounds) {}

    private static final long TOP_CACHE_MS = 30_000L;
    private static final int TOP_SIZE = 10;

    private final ProstPlugin plugin;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Prost-Stats");
        t.setDaemon(true);
        return t;
    });
    private final Map<Field, List<Row>> top = new ConcurrentHashMap<>();
    private final Map<Field, Long> topAt = new ConcurrentHashMap<>();
    private Connection conn;

    public Stats(ProstPlugin plugin) { this.plugin = plugin; }

    public void open() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");   // Paper ships sqlite-jdbc; the plugin classloader needs the explicit load
        } catch (ClassNotFoundException e) {
            throw new SQLException("sqlite-jdbc missing", e);
        }
        conn = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "stats.db").getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS stats (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, sips_drunk INTEGER NOT NULL DEFAULT 0, "
                    + "sips_water INTEGER NOT NULL DEFAULT 0, sips_given INTEGER NOT NULL DEFAULT 0, won INTEGER NOT NULL DEFAULT 0, "
                    + "lost INTEGER NOT NULL DEFAULT 0, roulette_hits INTEGER NOT NULL DEFAULT 0, jokers_used INTEGER NOT NULL DEFAULT 0, "
                    + "rules_made INTEGER NOT NULL DEFAULT 0, rounds INTEGER NOT NULL DEFAULT 0, last_played INTEGER NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS rounds (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT NOT NULL, host TEXT NOT NULL, "
                    + "started INTEGER NOT NULL, ended INTEGER NOT NULL, members INTEGER NOT NULL, challenges INTEGER NOT NULL)");
        }
    }

    public void add(UUID id, String name, Field field, int n) {
        if (n == 0 || conn == null) return;
        long now = System.currentTimeMillis();
        String c = field.column;
        String sql = "INSERT INTO stats (uuid, name, " + c + ", last_played) VALUES (?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET "
                + c + " = " + c + " + excluded." + c + ", name = excluded.name, last_played = excluded.last_played";
        worker.execute(() -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id.toString());
                ps.setString(2, name);
                ps.setInt(3, n);
                ps.setLong(4, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("stats " + c + " for " + name + " not written: " + e.getMessage());
            }
        });
    }

    public void roundEnded(Round r, long endedAt) {
        if (conn == null) return;
        String code = r.code, host = r.host.toString();
        long started = r.createdAt;
        int members = r.members.size(), challenges = r.challenges;
        worker.execute(() -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO rounds (code, host, started, ended, members, challenges) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, code);
                ps.setString(2, host);
                ps.setLong(3, started);
                ps.setLong(4, endedAt);
                ps.setInt(5, members);
                ps.setInt(6, challenges);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("round history not written: " + e.getMessage());
            }
        });
    }

    public void row(UUID id, Consumer<Row> then) {
        if (conn == null) { then.accept(null); return; }
        worker.execute(() -> {
            Row found = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM stats WHERE uuid = ?")) {
                ps.setString(1, id.toString());
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) found = read(rs); }
            } catch (SQLException e) {
                plugin.getLogger().warning("stats read failed: " + e.getMessage());
            }
            Row result = found;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> then.accept(result));
        });
    }

    public void top(Field field, Consumer<List<Row>> then) {
        List<Row> cached = top.get(field);
        if (cached != null && System.currentTimeMillis() - topAt.getOrDefault(field, 0L) < TOP_CACHE_MS) { then.accept(cached); return; }
        if (conn == null) { then.accept(List.of()); return; }
        worker.execute(() -> {
            List<Row> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM stats ORDER BY " + field.column + " DESC LIMIT " + TOP_SIZE)) {
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) rows.add(read(rs)); }
            } catch (SQLException e) {
                plugin.getLogger().warning("stats top failed: " + e.getMessage());
            }
            top.put(field, List.copyOf(rows));
            topAt.put(field, System.currentTimeMillis());
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> then.accept(List.copyOf(rows)));
        });
    }

    private static Row read(ResultSet rs) throws SQLException {
        return new Row(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getInt("sips_drunk"), rs.getInt("sips_water"),
                rs.getInt("sips_given"), rs.getInt("won"), rs.getInt("lost"), rs.getInt("roulette_hits"), rs.getInt("jokers_used"),
                rs.getInt("rules_made"), rs.getInt("rounds"));
    }

    /** inline at disable: let the queued writes finish, then close */
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) plugin.getLogger().warning("stats: writes still queued at shutdown were dropped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("stats.db close: " + e.getMessage());
        }
    }
}
