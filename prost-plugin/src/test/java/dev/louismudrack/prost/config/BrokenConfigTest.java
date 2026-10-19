package dev.louismudrack.prost.config;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A config.yml that does not parse: the last good copy answers (a MySQL server must not switch to SQLite), and the log
 * names the kind of error and its line, never the line's text (a password).
 */
class BrokenConfigTest {
    private static final String SECRET = "s3cret";
    private static final String LAST_GOOD_PASSWORD = "l4stg00d";
    private static final String LAST_GOOD_HOST = "db.example.invalid";
    private static final String LAST_GOOD = ".config.last-good.yml";
    private LogCapture log;
    private ServerMock server;

    @BeforeEach
    void mock() {
        server = TestServer.mock();
        log = LogCapture.root();
    }

    @AfterEach
    void unmock() {
        log.close();
        MockBukkit.unmock();
    }

    private static String shipped() {
        try (InputStream in = ProstPlugin.class.getResourceAsStream("/config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String changed(String from, String to) {
        String shipped = shipped(), out = shipped.replace(from, to);
        assertNotEquals(shipped, out, from);
        return out;
    }

    /** each prefix makes YAML fail on the password line in its own way: scanner (% @ `), alias (*), tag (! !!) */
    private static String brokenPassword(String prefix) { return changed("password: \"\"", "password: " + prefix + SECRET); }

    /** what a MySQL server parsed before its config.yml broke */
    private static String mysql() {
        return changed("type: sqlite", "type: mysql").replace("password: \"\"", "password: " + LAST_GOOD_PASSWORD)
                .replace("host: localhost", "host: " + LAST_GOOD_HOST);
    }

    private static String afkMinutes(int minutes) { return changed("minutes: 5", "minutes: " + minutes); }

    private static void write(File dataFolder, String name, String content) {
        try {
            Files.createDirectories(dataFolder.toPath());
            Files.writeString(dataFolder.toPath().resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> configWarnings() {
        return log.records().stream().map(LogRecord::getMessage).filter(m -> m.startsWith("config.yml")).toList();
    }

    private List<LogRecord> configRecords() { return log.records().stream().filter(r -> r.getMessage().contains("config.yml")).toList(); }

    private static String all(List<LogRecord> records) { return records.stream().map(LogCapture::text).toList().toString(); }

    @ParameterizedTest
    @ValueSource(strings = {"%", "@", "`", "*", "!!", "!"})
    void prostEnablesAndThePasswordNeverReachesTheLog(String prefix) {
        ProstPlugin plugin = TestServer.start(server, folder -> write(folder, "config.yml", brokenPassword(prefix)));
        assertTrue(plugin.isEnabled());
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        assertFalse(configWarnings().isEmpty(), all(log.records()));
        for (LogRecord r : log.records()) assertFalse(LogCapture.text(r).contains(SECRET), LogCapture.text(r));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "*", "!!", "!"})
    void theWarningNamesTheLine(String prefix) {
        TestServer.start(server, folder -> write(folder, "config.yml", brokenPassword(prefix)));
        List<String> warnings = configWarnings();
        assertFalse(warnings.isEmpty());
        for (String w : warnings) assertTrue(w.matches("(?s).*line \\d+, column \\d+.*"), w);
    }

    @Test
    void aBrokenConfigKeepsTheLastGoodStorage() {
        ProstPlugin plugin = TestServer.start(server);
        write(plugin.getDataFolder(), LAST_GOOD, mysql());
        write(plugin.getDataFolder(), "config.yml", brokenPassword("%"));
        log.clear();

        PluginConfig parsed = PluginConfig.parse(plugin.files().config(problem -> { }), warning -> { });
        assertEquals("mysql", parsed.storage().type());
        assertEquals(LAST_GOOD_HOST, parsed.storage().host());
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        assertEquals("mysql", plugin.pluginConfig().storage().type(), "/prost reload keeps the last good settings");

        List<LogRecord> records = configRecords();
        assertEquals(1, records.size(), all(records));
        assertEquals(Level.SEVERE, records.get(0).getLevel());
        String line = records.get(0).getMessage();
        assertTrue(line.matches("config\\.yml is unreadable \\(ScannerException at line \\d+, column \\d+\\).*"), line);
        assertTrue(line.contains(LAST_GOOD), line);
    }

    @Test
    void theStartUsesTheLastGoodCopyAndSaysSoOnce() {
        ProstPlugin plugin = TestServer.start(server, folder -> {
            write(folder, LAST_GOOD, afkMinutes(7));
            write(folder, "config.yml", brokenPassword("%"));
        });
        assertEquals(7, plugin.pluginConfig().afkMinutes());
        List<LogRecord> records = configRecords();
        assertEquals(1, records.size(), all(records));
        assertEquals(Level.SEVERE, records.get(0).getLevel());
        assertEquals(afkMinutes(7), read(plugin.getDataFolder().toPath().resolve(LAST_GOOD)), "a broken file never replaces the copy");
    }

    @Test
    void withoutALastGoodCopyTheShippedDefaultsApply() {
        ProstPlugin plugin = TestServer.start(server, folder ->
                write(folder, "config.yml", changed("type: sqlite", "type: mysql").replace("password: \"\"", "password: %" + SECRET)));
        assertEquals("sqlite", plugin.pluginConfig().storage().type());
        assertFalse(Files.exists(plugin.getDataFolder().toPath().resolve(LAST_GOOD)));
        List<LogRecord> records = configRecords();
        assertEquals(1, records.size(), all(records));
        assertEquals(Level.SEVERE, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("shipped defaults"), records.get(0).getMessage());
    }

    @Test
    void aGoodParseRefreshesTheCopy() {
        ProstPlugin plugin = TestServer.start(server, folder -> write(folder, "config.yml", afkMinutes(7)));
        Path folder = plugin.getDataFolder().toPath();
        assertEquals(read(folder.resolve("config.yml")), read(folder.resolve(LAST_GOOD)), "the copy is config.yml as parsed");
        write(plugin.getDataFolder(), "config.yml", afkMinutes(9));
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        assertEquals(9, plugin.pluginConfig().afkMinutes());
        assertEquals(afkMinutes(9), read(folder.resolve(LAST_GOOD)));
        assertFalse(Files.exists(folder.resolve(LAST_GOOD + ".tmp")), "written through a temp file and moved");
    }

    /** reload only: a start with the MySQL copy would open a connection */
    @Test
    void noLogRecordContainsAConfigValue() {
        ProstPlugin plugin = TestServer.start(server);
        write(plugin.getDataFolder(), LAST_GOOD, mysql());
        write(plugin.getDataFolder(), "config.yml", brokenPassword("@"));
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        assertFalse(configRecords().isEmpty());
        for (LogRecord r : log.records())
            for (String value : List.of(SECRET, LAST_GOOD_PASSWORD, LAST_GOOD_HOST)) assertFalse(LogCapture.text(r).contains(value), LogCapture.text(r));
        assertTrue(plugin.isEnabled());
    }

    /**
     * a crash or a full disk can leave config.yml empty: the copy answers and stays, and the file is not filled with the
     * shipped defaults (storage: sqlite) - which the next parse would have taken as the new last good settings
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "# nothing but a comment\n", "{}\n"})
    void anEmptyConfigKeepsTheLastGoodCopy(String empty) {
        ProstPlugin plugin = TestServer.start(server, folder -> {
            write(folder, LAST_GOOD, afkMinutes(7));
            write(folder, "config.yml", empty);
        });
        Path folder = plugin.getDataFolder().toPath();
        assertEquals(7, plugin.pluginConfig().afkMinutes());
        assertEquals(empty, read(folder.resolve("config.yml")), "not filled with the shipped defaults");
        assertEquals(afkMinutes(7), read(folder.resolve(LAST_GOOD)), "the copy stays");
        List<LogRecord> records = configRecords();
        assertEquals(1, records.size(), all(records));
        assertEquals(Level.SEVERE, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().startsWith("config.yml is empty - the last good settings"), records.get(0).getMessage());
    }

    @Test
    void anEmptyConfigReadsTheMysqlCopy() {
        ProstPlugin plugin = TestServer.start(server);
        write(plugin.getDataFolder(), LAST_GOOD, mysql());
        write(plugin.getDataFolder(), "config.yml", "");
        PluginConfig parsed = PluginConfig.parse(plugin.files().config(problem -> { }), warning -> { });
        assertEquals("mysql", parsed.storage().type());
        assertEquals(mysql(), read(plugin.getDataFolder().toPath().resolve(LAST_GOOD)), "the copy stays");
    }

    /** a first start with an empty file: nothing to fall back to, so the shipped defaults fill it as before */
    @Test
    void anEmptyConfigWithoutACopyGetsTheDefaults() {
        ProstPlugin plugin = TestServer.start(server, folder -> write(folder, "config.yml", ""));
        assertEquals(PluginConfig.DEFAULTS.storage().type(), plugin.pluginConfig().storage().type());
        assertTrue(read(plugin.getDataFolder().toPath().resolve("config.yml")).contains("storage:"), "the defaults were added");
        assertTrue(configRecords().stream().noneMatch(r -> r.getLevel() == Level.SEVERE), all(configRecords()));
    }
}
