package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What the draw is told about the participants themselves. */
class SituationTest {
    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void aFullFoodBarAmongTheParticipantsIsSeen() {
        ServerMock server = TestServer.mock();
        ProstPlugin plugin = TestServer.start(server);
        PlayerMock anna = server.addPlayer("Anna"), ben = server.addPlayer("Ben");
        anna.setFoodLevel(12);
        ben.setFoodLevel(19);
        assertFalse(Situation.of(plugin.hooks(), List.of(anna, ben)).anyoneFull(), "both can eat");
        ben.setFoodLevel(20);
        assertTrue(Situation.of(plugin.hooks(), List.of(anna, ben)).anyoneFull(), "Ben cannot eat");
    }
}
