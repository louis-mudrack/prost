package dev.louismudrack.prost.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** The SQL that differs between SQLite and MySQL/MariaDB. */
public enum Dialect {
    SQLITE("org.sqlite.JDBC") {
        @Override String autoId() { return "INTEGER PRIMARY KEY AUTOINCREMENT"; }
        @Override String insertIgnore() { return "INSERT OR IGNORE INTO "; }
        @Override String upsert(List<String> assignments) { return " ON CONFLICT(uuid) DO UPDATE SET " + String.join(", ", assignments); }
        @Override String incoming(String column) { return "excluded." + column; }

        @Override
        void createIndex(Connection c, String table, String index, String column) throws SQLException {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE INDEX IF NOT EXISTS " + index + " ON " + table + " (" + column + ")");
            }
        }
    },
    MYSQL("org.mariadb.jdbc.Driver") {
        @Override String autoId() { return "BIGINT AUTO_INCREMENT PRIMARY KEY"; }
        @Override String insertIgnore() { return "INSERT IGNORE INTO "; }
        @Override String upsert(List<String> assignments) { return " ON DUPLICATE KEY UPDATE " + String.join(", ", assignments); }
        @Override String incoming(String column) { return "VALUES(" + column + ")"; }

        /** MySQL 8 has no CREATE INDEX IF NOT EXISTS: look first */
        @Override
        void createIndex(Connection c, String table, String index, String column) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM information_schema.statistics "
                    + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?")) {
                ps.setString(1, table);
                ps.setString(2, index);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return;
                }
            }
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE INDEX " + index + " ON " + table + " (" + column + ")");
            } catch (SQLException e) {
                if (e.getErrorCode() != DUPLICATE_KEY_NAME) throw e;   // another server starting at the same time was faster
            }
        }
    };

    private static final int DUPLICATE_KEY_NAME = 1061;

    static final List<String> COUNTERS = List.of("sips_drunk", "sips_water", "sips_given", "won", "lost", "roulette_hits",
            "jokers_used", "rules_made", "rounds");

    private final String driver;

    Dialect(String driver) { this.driver = driver; }

    String driver() { return driver; }

    abstract String autoId();

    abstract String insertIgnore();

    abstract String upsert(List<String> assignments);

    abstract String incoming(String column);

    abstract void createIndex(Connection c, String table, String index, String column) throws SQLException;

    /** one counter as a delta: never overwrites what another server wrote */
    String addSql(String table, String column) {
        return "INSERT INTO " + table + " (uuid, name, " + column + ", last_played) VALUES (?, ?, ?, ?)" + upsert(List.of(
                column + " = " + column + " + " + incoming(column), "name = " + incoming("name"), "last_played = " + incoming("last_played")));
    }

    /** one plain column (language, noticed) */
    String setSql(String table, String column) {
        return "INSERT INTO " + table + " (uuid, name, " + column + ") VALUES (?, ?, ?)"
                + upsert(List.of(column + " = " + incoming(column), "name = " + incoming("name")));
    }

    /** every counter with its absolute value (migration) */
    String importSql(String table) {
        List<String> cols = new ArrayList<>(List.of("name"));
        cols.addAll(COUNTERS);
        cols.add("last_played");
        return "INSERT INTO " + table + " (uuid, " + String.join(", ", cols) + ") VALUES (" + "?, ".repeat(cols.size()) + "?)"
                + upsert(cols.stream().map(c -> c + " = " + incoming(c)).toList());
    }
}
