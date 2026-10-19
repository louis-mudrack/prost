package dev.louismudrack.prost.meta;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/** What the console says about GitHub's answer (the request itself needs the network, which the test build has not). */
class UpdateCheckerTest {
    private LogCapture log;
    private ProstPlugin plugin;
    private UpdateChecker checker;

    @BeforeEach
    void start() {
        plugin = TestServer.start(TestServer.mock());
        log = LogCapture.on(plugin.getLogger());
        checker = new UpdateChecker(plugin);
    }

    @AfterEach
    void stop() {
        log.close();
        MockBukkit.unmock();
    }

    private static String release(String tag) { return "[{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":false}]"; }

    private void assertOneInfoLine(String expectedStart) {
        assertEquals(1, log.records().size(), log.messages().toString());
        LogRecord r = log.records().get(0);
        assertEquals(Level.INFO, r.getLevel());
        assertTrue(r.getMessage().startsWith(expectedStart), r.getMessage());
        assertNull(r.getThrown(), "no stack trace");
    }

    @Test
    void asksGitHubsReleases() {
        assertEquals("https://api.github.com/repos/louis-mudrack/prost/releases?per_page=20", UpdateChecker.API);
    }

    @Test
    void aNewerReleaseIsTold() {
        checker.answer(200, release("v9.0.0"), "0.3.1");
        assertOneInfoLine("Prost 9.0.0 is available");
    }

    @Test
    void theRunningVersionSaysNothing() {
        checker.answer(200, release("v0.3.1"), "0.3.1");
        assertTrue(log.records().isEmpty());
    }

    @Test
    void aRateLimitIsOneInfoLine() {
        checker.answer(403, "{\"message\":\"API rate limit exceeded for 203.0.113.7\"}", "0.3.1");
        assertOneInfoLine("update check failed: HTTP 403");
    }

    @Test
    void aBodyThatIsNotAReleaseListIsOneInfoLine() {
        checker.answer(200, "{\"message\":\"Not Found\"}", "0.3.1");
        assertOneInfoLine("update check failed: ");
        assertFalse(log.messages().get(0).contains("Not Found"), "the body is not repeated");
    }

    @Test
    void aBodyThatIsNotJsonIsOneInfoLine() {
        checker.answer(200, "<html>", "0.3.1");
        assertOneInfoLine("update check failed: ");
    }

    @Test
    void connectingGivesUpAfterTenSeconds() {
        HttpClient client = UpdateChecker.client();
        try {
            assertEquals(Optional.of(Duration.ofSeconds(10)), client.connectTimeout());
        } finally {
            client.shutdownNow();
        }
    }

    @Test
    void aFailureIsNamedByItsCause() {
        assertEquals("HTTP connect timed out", UpdateChecker.reason(new CompletionException(new IOException("HTTP connect timed out"))));
        assertEquals("ConnectException", UpdateChecker.reason(new CompletionException(new ConnectException())),
                "no message: the class name, not 'null'");
        assertEquals("IllegalStateException", UpdateChecker.reason(new IllegalStateException()));
    }
}
