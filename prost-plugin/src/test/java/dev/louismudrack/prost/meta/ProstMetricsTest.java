package dev.louismudrack.prost.meta;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

class ProstMetricsTest {
    @Test
    void roundSizesAreBucketed() {
        assertEquals("no round", ProstMetrics.bucket(List.of()));
        assertEquals("1", ProstMetrics.bucketOf(List.of(1)));
        assertEquals("2-3", ProstMetrics.bucketOf(List.of(2, 3)));
        assertEquals("4-5", ProstMetrics.bucketOf(List.of(4, 5, 4)));
        assertEquals("6+", ProstMetrics.bucketOf(List.of(8)));
    }

    @Test
    void registeredOnBstats() { assertEquals(34824, ProstMetrics.BSTATS_ID); }

    /** the test classes keep bStats unrelocated, which bStats refuses with an exception: it must not stop Prost */
    @Test
    void aBstatsFailureCostsOneWarning() {
        ProstPlugin plugin = TestServer.start(TestServer.mock());
        try (LogCapture log = LogCapture.root()) {
            assertDoesNotThrow(() -> ProstMetrics.start(plugin));
            List<LogRecord> warnings = log.records().stream().filter(r -> r.getLevel() == Level.WARNING).toList();
            assertEquals(1, warnings.size(), log.messages().toString());
            assertTrue(warnings.get(0).getMessage().startsWith("bStats"), warnings.get(0).getMessage());
        } finally {
            MockBukkit.unmock();
        }
    }
}
