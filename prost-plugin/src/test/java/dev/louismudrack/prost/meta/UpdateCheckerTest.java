package dev.louismudrack.prost.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test
    void noProjectOnModrinthYetIsNotWorthAnInfoLine() {
        assertEquals(Level.FINE, UpdateChecker.failureLevel(404));
    }

    @Test
    void otherFailuresAreTold() {
        assertEquals(Level.INFO, UpdateChecker.failureLevel(500));
        assertEquals(Level.INFO, UpdateChecker.failureLevel(429));
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
