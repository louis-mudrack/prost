package dev.louismudrack.prost;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.InventoryHolder;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;
import org.mockbukkit.mockbukkit.inventory.ChestInventoryMock;
import org.mockbukkit.mockbukkit.inventory.InventoryMock;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

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

    /** Prost's version as the build wrote it into plugin.yml */
    public static String version() {
        try (InputStream in = TestServer.class.getResourceAsStream("/plugin.yml")) {
            return String.valueOf(new Yaml().<Map<?, ?>>load(in).get("version"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * For beforeEnable: challenges.yml as the admin of an up-to-date install wrote it. Its marker says this version's new
     * entries were added before, so CatalogueMerge leaves the file as it is.
     */
    public static void catalogue(File dataFolder, String challenges) {
        try {
            Path folder = Files.createDirectories(dataFolder.toPath());
            Files.writeString(folder.resolve("challenges.yml"), challenges, StandardCharsets.UTF_8);
            Files.writeString(folder.resolve(".catalogue-version"), version(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** everything the player (or the console) was sent since the last call, as plain text */
    public static List<String> messages(MessageTarget target) {
        List<String> out = new ArrayList<>();
        for (Component c = target.nextComponentMessage(); c != null; c = target.nextComponentMessage())
            out.add(PlainTextComponentSerializer.plainText().serialize(c));
        return out;
    }

    private static final class MenuServer extends ServerMock {
        /** one folder per JVM under the module's build folder; a shutdown hook deletes it with everything the tests put there */
        private static final Path WORLD_CONTAINERS = worldContainers();
        private File worldContainer;

        /** MockBukkit has none (it throws); Returns looks here for the folder of a world that is not loaded */
        @Override
        public File getWorldContainer() {
            try {
                if (worldContainer == null)
                    worldContainer = Files.createTempDirectory(WORLD_CONTAINERS, "prost-worlds").toFile();
                return worldContainer;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static Path worldContainers() {
            try {
                Path root = Files.createTempDirectory(Files.createDirectories(Path.of("build", "tmp", "test-worlds")), "jvm");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteTree(root)));
                return root;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static void deleteTree(Path root) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
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
