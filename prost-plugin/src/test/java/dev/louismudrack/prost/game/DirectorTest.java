package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The "was paused" memory behind the resume rule belongs to a round, not to its code: codes are reused. */
class DirectorTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

    @Test
    void aNewRoundWithAClosedRoundsCodeIsNotResumed() {
        Director director = new Director(null);
        Round old = new Round("AB12", A, Pace.NORMAL, 0);
        old.paused = true;
        assertFalse(director.resumed(old));
        Round reused = new Round("AB12", B, Pace.NORMAL, 0);
        assertFalse(director.resumed(reused), "the code is reused, the pause is not");
        old.paused = false;
        assertTrue(director.resumed(old), "the paused round itself does resume");
        assertFalse(director.resumed(old), "only on the first tick after the pause");
    }

    @Test
    void aClosedRoundIsForgotten() {
        ServerMock server = TestServer.mock();
        try {
            ProstPlugin plugin = TestServer.start(server);
            PlayerMock host = server.addPlayer("Host");
            host.performCommand("prost start");
            plugin.rounds().of(host.getUniqueId()).paused = true;
            server.getScheduler().performTicks(10);
            assertEquals(1, plugin.director().watchedRounds());
            host.performCommand("prost end");
            server.getScheduler().performTicks(10);
            assertEquals(0, plugin.director().watchedRounds());
        } finally {
            MockBukkit.unmock();
        }
    }
}
