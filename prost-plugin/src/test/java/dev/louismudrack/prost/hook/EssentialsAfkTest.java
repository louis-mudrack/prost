package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

/** A plugin named Essentials that is not EssentialsX (here: no EssentialsX classes at all) must not stop Prost. */
class EssentialsAfkTest {
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

    private List<LogRecord> aboutEssentials() { return log.records().stream().filter(r -> String.valueOf(r.getMessage()).contains("Essentials")).toList(); }

    @Test
    void aForeignEssentialsKeepsProstsOwnAfkDetection() {
        MockBukkit.createMockPlugin("Essentials");
        ProstPlugin plugin = TestServer.start(server);
        assertTrue(plugin.isEnabled());
        List<LogRecord> records = aboutEssentials();
        assertEquals(1, records.size(), records.stream().map(LogRecord::getMessage).toList().toString());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("not EssentialsX"), records.get(0).getMessage());

        PlayerMock p = server.addPlayer();
        assertFalse(plugin.hooks().afk(p));
        assertEquals(1, aboutEssentials().size(), "Prost's own detection answers: no failed EssentialsX check");
    }
}
