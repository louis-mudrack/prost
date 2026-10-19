package dev.louismudrack.prost;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.InventoryHolder;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

    public static ProstPlugin start(ServerMock server) { return start(server, dataFolder -> { }); }

    /** beforeEnable gets Prost's data folder (MockBukkit names it after the version) before onEnable runs */
    public static ProstPlugin start(ServerMock server, Consumer<File> beforeEnable) {
        if (!(server instanceof MenuServer)) throw new IllegalArgumentException("start Prost on TestServer.mock(), not MockBukkit.mock()");
        server.addSimpleWorld("world");
        ProstPlugin plugin = (ProstPlugin) server.getPluginManager().loadPlugin(ProstPlugin.class, new Object[0]);
        beforeEnable.accept(plugin.getDataFolder());
        // what MockBukkit.load does between loading and enabling
        server.getPluginManager().addPermissions(plugin.getPluginMeta().getPermissions().stream()
                .filter(permission -> server.getPluginManager().getPermission(permission.getName()) == null).toList());
        server.getPluginManager().enablePlugin(plugin);
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
        /** under the module's build folder: gradle clean removes what the tests leave there */
        private static final Path WORLD_CONTAINERS = Path.of("build", "tmp", "test-worlds");
        private File worldContainer;

        /** MockBukkit has none (it throws); Returns looks here for the folder of a world that is not loaded */
        @Override
        public File getWorldContainer() {
            try {
                if (worldContainer == null)
                    worldContainer = Files.createTempDirectory(Files.createDirectories(WORLD_CONTAINERS), "prost-worlds").toFile();
                return worldContainer;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InventoryMock createInventory(InventoryHolder owner, int size, Component title) {
            return new ChestInventoryMock(owner, size) {
                @Override
                public InventoryHolder getHolder(boolean useSnapshot) { return getHolder(); }
            };
        }
    }
}
