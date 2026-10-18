package dev.louismudrack.prost.menu;

import dev.louismudrack.prost.TestServer;
import org.bukkit.inventory.InventoryHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.PlayerSimulation;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Part 1's rule: an inventory never opens inside a click event; the action runs one tick later. */
class MenuClickTest {
    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        TestServer.start(server);
        player = server.addPlayer("Host");
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private InventoryHolder holder() { return player.getOpenInventory().getTopInventory().getHolder(false); }

    @Test
    void aClickActsOnTheNextTick() {
        player.performCommand("prost");
        assertInstanceOf(MainMenu.class, holder());
        new PlayerSimulation(player).simulateInventoryClick(player.getOpenInventory(), 11);   // MainMenu slot 11: open a round
        assertInstanceOf(MainMenu.class, holder(), "nothing opens inside the click event");
        server.getScheduler().performTicks(1);
        assertInstanceOf(ChoiceMenu.class, holder(), "the pace picker opens one tick later");
    }
}
