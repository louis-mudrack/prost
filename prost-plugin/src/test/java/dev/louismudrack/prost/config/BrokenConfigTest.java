package dev.louismudrack.prost.config;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** A config.yml that does not parse: the warning names the problem and its line, never the line's text (a password). */
class BrokenConfigTest {
    private static final String SECRET = "s3cret";
    private final List<LogRecord> logged = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override public void publish(LogRecord r) { logged.add(r); }
        @Override public void flush() { }
        @Override public void close() { }
    };
    private ServerMock server;

    @BeforeEach
    void mock() {
        server = TestServer.mock();
        Logger.getLogger("").addHandler(capture);
    }

    @AfterEach
    void unmock() {
        Logger.getLogger("").removeHandler(capture);
        MockBukkit.unmock();
    }

    private static void writeConfigWithBrokenPassword(File dataFolder) {
        try (InputStream in = ProstPlugin.class.getResourceAsStream("/config.yml")) {
            String shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String broken = shipped.replace("password: \"\"", "password: %" + SECRET);
            assertNotEquals(shipped, broken);
            Files.createDirectories(dataFolder.toPath());
            Files.writeString(dataFolder.toPath().resolve("config.yml"), broken, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String text(LogRecord r) {
        String thrown = r.getThrown() == null ? "" : String.valueOf(r.getThrown());
        return r.getMessage() + " " + thrown;
    }

    private List<String> configWarnings() {
        return logged.stream().map(LogRecord::getMessage).filter(m -> m.startsWith("config.yml")).toList();
    }

    @Test
    void thePasswordNeverReachesTheLog() {
        ProstPlugin plugin = TestServer.start(server, BrokenConfigTest::writeConfigWithBrokenPassword);
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        assertTrue(plugin.isEnabled());
        assertFalse(configWarnings().isEmpty(), logged.stream().map(BrokenConfigTest::text).toList().toString());
        for (LogRecord r : logged) assertFalse(text(r).contains(SECRET), text(r));
    }

    @Test
    void theWarningNamesTheLine() {
        TestServer.start(server, BrokenConfigTest::writeConfigWithBrokenPassword);
        List<String> warnings = configWarnings();
        assertFalse(warnings.isEmpty());
        for (String w : warnings) assertTrue(w.matches("(?s).*line \\d+, column \\d+.*"), w);
    }
}
