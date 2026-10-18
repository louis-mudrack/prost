package dev.louismudrack.prost.storage;

import dev.louismudrack.prost.config.PluginConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * All-time numbers per player in SQLite (stats.db) or MySQL/MariaDB. One background thread owns the connection, so
 * reads and writes never race; counters are deltas (sips = sips + ?), so servers sharing a database never overwrite
 * each other. A dead connection is reopened before the next statement, and a statement cut by a lost connection
 * (SQLState class 08) is retried once.
 */
public final class SqlStatsStore implements StatsStore {
    private static final long TOP_CACHE_MS = 30_000L;
    private static final int TOP_SIZE = 10;
    private static final int VALID_TIMEOUT_SECONDS = 2;
    private static final long CLOSE_WAIT_SECONDS = 5;
    private static final long WARN_GAP_MS = 60_000L;
    private static final String CONNECTION_LOST = "08";

    private interface Work<T> { T run(Connection c) throws SQLException; }

    private final Dialect dialect;
    private final String url, user, password, stats, rounds;
    private final Logger log;
    private final Executor sync;
    private final ExecutorService worker;
    private final Map<StatsField, List<StatsRow>> top = new ConcurrentHashMap<>();
    private final Map<StatsField, Long> topAt = new ConcurrentHashMap<>();
    private final Set<StatsField> refreshing = ConcurrentHashMap.newKeySet();
    private Connection conn;   // worker thread only
    private boolean schemaReady;   // worker thread only
    private long lastWarnAt;       // worker thread only

    private SqlStatsStore(Dialect dialect, String url, String user, String password, String prefix, Logger log, Executor sync,
                          ExecutorService worker) {
        this.dialect = dialect;
        this.url = url;
        this.user = user;
        this.password = password;
        this.stats = prefix + "stats";
        this.rounds = prefix + "rounds";
        this.log = log;
        this.sync = sync;
        this.worker = worker;
    }

    private static ExecutorService singleWorker() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Prost-Stats");
            t.setDaemon(true);
            return t;
        });
    }

    public static SqlStatsStore sqlite(File file, String prefix, Logger log, Executor sync) {
        return sqlite(file, prefix, log, sync, singleWorker());
    }

    /** tests pass a worker they drain by hand */
    static SqlStatsStore sqlite(File file, String prefix, Logger log, Executor sync, ExecutorService worker) {
        return new SqlStatsStore(Dialect.SQLITE, "jdbc:sqlite:" + file.getAbsolutePath(), null, null, prefix, log, sync, worker);
    }

    public static SqlStatsStore mysql(PluginConfig.Storage s, Logger log, Executor sync) {
        String url = "jdbc:mariadb://" + s.host() + ":" + s.port() + "/" + s.database() + (s.parameters().isBlank() ? "" : "?" + s.parameters());
        return new SqlStatsStore(Dialect.MYSQL, url, s.user(), s.password(), s.tablePrefix(), log, sync, singleWorker());
    }

    /** blocking, at enable: connects and creates the tables; if that fails, the next statement tries again */
    public void open() throws SQLException {
        try {
            worker.submit(() -> connection()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("interrupted while opening the stats database", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof SQLException s ? s : new SQLException(e.getCause());
        }
    }

    /** worker thread: a live connection with the schema in place (also after a database restart or wipe) */
    private Connection connection() throws SQLException {
        if (conn != null && conn.isValid(VALID_TIMEOUT_SECONDS)) return conn;
        closeQuietly();
        try {
            Class.forName(dialect.driver());   // Paper's plugin classloader needs the explicit load
        } catch (ClassNotFoundException e) {
            throw new SQLException(dialect.driver() + " is missing", e);
        }
        Connection fresh = user == null ? DriverManager.getConnection(url) : DriverManager.getConnection(url, user, password);
        try {
            if (!schemaReady) {
                schema(fresh);
                schemaReady = true;
            }
        } catch (SQLException e) {
            fresh.close();
            throw e;
        }
        conn = fresh;
        return conn;
    }

    private void closeQuietly() {
        if (conn == null) return;
        try {
            conn.close();
        } catch (SQLException e) {
            log.fine("closing a dead stats connection: " + e.getMessage());
        }
        conn = null;
    }

    private void schema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + stats + " (uuid VARCHAR(36) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL, "
                    + "sips_drunk INT NOT NULL DEFAULT 0, sips_water INT NOT NULL DEFAULT 0, sips_given INT NOT NULL DEFAULT 0, "
                    + "won INT NOT NULL DEFAULT 0, lost INT NOT NULL DEFAULT 0, roulette_hits INT NOT NULL DEFAULT 0, "
                    + "jokers_used INT NOT NULL DEFAULT 0, rules_made INT NOT NULL DEFAULT 0, rounds INT NOT NULL DEFAULT 0, "
                    + "last_played BIGINT NOT NULL DEFAULT 0, language VARCHAR(16) NULL, noticed INT NOT NULL DEFAULT 0)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS " + rounds + " (id " + dialect.autoId() + ", code VARCHAR(8) NOT NULL, "
                    + "host VARCHAR(36) NOT NULL, started BIGINT NOT NULL, ended BIGINT NOT NULL, members INT NOT NULL, "
                    + "challenges INT NOT NULL, UNIQUE (code, started))");
        }
        for (StatsField f : StatsField.values()) dialect.createIndex(c, stats, stats + "_" + f.column, f.column);
    }

    /** worker thread: one retry after a lost connection, so a database restart costs no counter */
    private <T> T attempt(Work<T> work) throws SQLException {
        try {
            return work.run(connection());
        } catch (SQLException e) {
            if (e.getSQLState() == null || !e.getSQLState().startsWith(CONNECTION_LOST)) throw e;
            closeQuietly();
            return work.run(connection());
        }
    }

    private CompletableFuture<Void> submit(String what, Work<Void> work) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                attempt(work);
                done.complete(null);
            } catch (SQLException e) {
                warn(what + " not written: " + e.getMessage());
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    /** a database that stays down must not flood the console: one warning a minute */
    private void warn(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarnAt < WARN_GAP_MS) return;
        lastWarnAt = now;
        log.warning(message);
    }

    @Override
    public void add(UUID id, String name, StatsField field, int n) {
        if (n == 0) return;
        long now = System.currentTimeMillis();
        String sql = dialect.addSql(stats, field.column);
        submit("stats " + field.column + " for " + name, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id.toString());
                ps.setString(2, name);
                ps.setInt(3, n);
                ps.setLong(4, now);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void roundEnded(String code, UUID host, long started, long ended, int members, int challenges) {
        importRound(code, host.toString(), started, ended, members, challenges);
    }

    @Override
    public CompletableFuture<Void> importRound(String code, String host, long started, long ended, int members, int challenges) {
        return submit("round " + code, c -> {
            try (PreparedStatement ps = c.prepareStatement(dialect.insertIgnore() + rounds
                    + " (code, host, started, ended, members, challenges) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, code);
                ps.setString(2, host);
                ps.setLong(3, started);
                ps.setLong(4, ended);
                ps.setInt(5, members);
                ps.setInt(6, challenges);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void row(UUID id, Consumer<StatsRow> then) {
        worker.execute(() -> {
            StatsRow found = null;
            try {
                found = attempt(c -> {
                    try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + stats + " WHERE uuid = ?")) {
                        ps.setString(1, id.toString());
                        try (ResultSet rs = ps.executeQuery()) { return rs.next() ? read(rs) : null; }
                    }
                });
            } catch (SQLException e) {
                warn("stats read failed: " + e.getMessage());
            }
            StatsRow result = found;
            sync.execute(() -> then.accept(result));
        });
    }

    @Override
    public void top(StatsField field, Consumer<List<StatsRow>> then) {
        List<StatsRow> cached = fresh(field);
        if (cached != null) {
            then.accept(cached);
            return;
        }
        worker.execute(() -> {
            List<StatsRow> rows = loadTop(field);
            sync.execute(() -> then.accept(rows));
        });
    }

    @Override
    public List<StatsRow> cachedTop(StatsField field) {
        List<StatsRow> cached = fresh(field);
        if (cached != null) return cached;
        if (refreshing.add(field)) worker.execute(() -> {
            try {
                loadTop(field);
            } finally {
                refreshing.remove(field);
            }
        });
        return top.getOrDefault(field, List.of());
    }

    private List<StatsRow> fresh(StatsField field) {
        List<StatsRow> cached = top.get(field);
        return cached != null && System.currentTimeMillis() - topAt.getOrDefault(field, 0L) < TOP_CACHE_MS ? cached : null;
    }

    /** worker thread; a failed load keeps the last known list instead of caching an empty one */
    private List<StatsRow> loadTop(StatsField field) {
        try {
            List<StatsRow> rows = attempt(c -> {
                List<StatsRow> out = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT * FROM " + stats + " ORDER BY " + field.column + " DESC LIMIT " + TOP_SIZE);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(read(rs));
                }
                return List.copyOf(out);
            });
            top.put(field, rows);
            topAt.put(field, System.currentTimeMillis());
            return rows;
        } catch (SQLException e) {
            warn("stats top failed: " + e.getMessage());
            return top.getOrDefault(field, List.of());
        }
    }

    @Override
    public void prefs(UUID id, Consumer<Prefs> then) {
        worker.execute(() -> {
            Prefs p = Prefs.NONE;
            try {
                p = attempt(c -> {
                    try (PreparedStatement ps = c.prepareStatement("SELECT language, noticed FROM " + stats + " WHERE uuid = ?")) {
                        ps.setString(1, id.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            return rs.next() ? new Prefs(rs.getString("language"), rs.getInt("noticed") != 0) : Prefs.NONE;
                        }
                    }
                });
            } catch (SQLException e) {
                warn("player settings read failed: " + e.getMessage());
            }
            Prefs result = p;
            sync.execute(() -> then.accept(result));
        });
    }

    @Override
    public void setLanguage(UUID id, String name, String language) { set(id, name, "language", language); }

    @Override
    public void markNoticed(UUID id, String name) { set(id, name, "noticed", 1); }

    private void set(UUID id, String name, String column, Object value) {
        String sql = dialect.setSql(stats, column);
        submit("player setting " + column + " for " + name, c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id.toString());
                ps.setString(2, name);
                if (value == null) ps.setNull(3, Types.VARCHAR);
                else ps.setObject(3, value);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> importRow(StatsRow r, long lastPlayed) {
        return submit("imported stats of " + r.name(), c -> {
            try (PreparedStatement ps = c.prepareStatement(dialect.importSql(stats))) {
                int i = 1;
                ps.setString(i++, r.id().toString());
                ps.setString(i++, r.name());
                for (int v : new int[]{r.drunk(), r.water(), r.given(), r.won(), r.lost(), r.roulette(), r.jokers(), r.rules(), r.rounds()})
                    ps.setInt(i++, v);
                ps.setLong(i, lastPlayed);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> flush() { return CompletableFuture.runAsync(() -> { }, worker); }

    /** test hook: rows in the rounds table (blocking) */
    long countRounds() throws Exception {
        return worker.submit(() -> attempt(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + rounds)) {
                rs.next();
                return rs.getLong(1);
            }
        })).get();
    }

    private static StatsRow read(ResultSet rs) throws SQLException {
        return new StatsRow(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getInt("sips_drunk"),
                rs.getInt("sips_water"), rs.getInt("sips_given"), rs.getInt("won"), rs.getInt("lost"), rs.getInt("roulette_hits"),
                rs.getInt("jokers_used"), rs.getInt("rules_made"), rs.getInt("rounds"));
    }

    @Override
    public void close() {
        worker.shutdown();
        try {
            if (!worker.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS)) log.warning("stats: writes still queued at shutdown were dropped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (conn != null) conn.close();
        } catch (SQLException e) {
            log.warning("stats database close: " + e.getMessage());
        }
    }
}
