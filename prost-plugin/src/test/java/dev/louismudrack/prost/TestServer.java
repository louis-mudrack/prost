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
 * Every suite that loads Prost starts with {@code server = TestServer.mock(); plugin = TestServer.start(server);}.
 * /prost runs as on a real server: player.performCommand("prost ...") or server.dispatchCommand(sender, "prost ...").
 */
public final class TestServer {
    private TestServer() {}

    /**
     * The server for Prost tests: MockBukkit.mock() plus Inventory#getHolder(boolean), which the menus use on every
     * inventory event and MockBukkit 4.116.3 does not implement (its "not implemented" exception skips a test).
     */
    public static ServerMock mock() { return MockBukkit.mock(new MenuServer()); }

    public static ProstPlugin start(ServerMock server) {
        if (!(server instanceof MenuServer)) throw new IllegalArgumentException("start Prost on TestServer.mock(), not MockBukkit.mock()");
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
