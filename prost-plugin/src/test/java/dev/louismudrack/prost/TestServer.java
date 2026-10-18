package dev.louismudrack.prost;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.InventoryHolder;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;

import java.util.ArrayList;
import java.util.List;

/**
 * Prost on a MockBukkit server (metrics and the update checker stay off: build.gradle.kts sets prost.offline).
 * /prost runs as on a real server: player.performCommand("prost ...") or server.dispatchCommand(sender, "prost ...").
 */
public final class TestServer {
    private TestServer() {}

    /**
     * MockBukkit.mock() for tests that click or close Prost menus: MockBukkit 4.116.3 has no Inventory#getHolder(boolean),
     * which the menus use, and its "not implemented" exception skips a test instead of failing it.
     */
    public static ServerMock mock() { return MockBukkit.mock(new MenuServer()); }

    public static ProstPlugin start(ServerMock server) {
        server.addSimpleWorld("world");
        ProstPlugin plugin = MockBukkit.load(ProstPlugin.class);
        // a real server registers commands (LifecycleEvents.COMMANDS) once startup is done; MockBukkit on the first dispatch
        server.dispatchCommand(server.getConsoleSender(), "");
        return plugin;
    }

    /** everything the player (or the console) was sent since the last call, as plain text */
    public static List<String> messages(MessageTarget target) {
        List<String> out = new ArrayList<>();
        for (Component c = target.nextComponentMessage(); c != null; c = target.nextComponentMessage())
            out.add(PlainTextComponentSerializer.plainText().serialize(c));
        return out;
    }

    private static final class MenuServer extends ServerMock {
        @Override
        public InventoryMock createInventory(InventoryHolder owner, int size, Component title) {
            return new ChestInventoryMock(owner, size) {
                @Override
                public InventoryHolder getHolder(boolean useSnapshot) { return getHolder(); }
            };
        }
    }
}
