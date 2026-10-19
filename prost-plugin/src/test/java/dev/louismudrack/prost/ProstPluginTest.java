package dev.louismudrack.prost;

import dev.louismudrack.prost.api.ProstApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProstPluginTest {
    private ServerMock server;
    private ProstPlugin plugin;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void enablesAndOffersTheApi() {
        assertTrue(plugin.isEnabled());
        assertNotNull(server.getServicesManager().load(ProstApi.class));
    }

    /** metrics is on by default and Prost is registered on bStats: only prost.offline (build.gradle.kts) keeps tests out */
    @Test
    void theTestBuildNeverStartsBstats() {
        assertTrue(Boolean.getBoolean("prost.offline"));
        assertTrue(plugin.pluginConfig().metrics());
        assertFalse(new File(plugin.getDataFolder().getParentFile(), "bStats").exists(), "bStats writes its config when it starts");
    }

    @Test
    void registersProstAndItsAlias() {
        assertNotNull(server.getCommandMap().getCommand("prost"));
        assertNotNull(server.getCommandMap().getCommand("drinks"));
        assertEquals("Prost - the drinking game", server.getCommandMap().getCommand("prost").getDescription());
    }

    /** Paper leaves out an alias another command already has; MockBukkit does not model that, so the check is tested alone */
    @Test
    void reportsAliasesPaperDidNotRegister() {
        Set<String> registered = Set.of("prost", "prost:prost", "drinks", "prost:drinks", "prost:trinkspiel");
        assertEquals(List.of("trinkspiel"), ProstPlugin.notRegistered(List.of("drinks", "trinkspiel"), registered));
        assertEquals(List.of(), ProstPlugin.notRegistered(List.of("Drinks"), registered));
    }

    /** Paper shows one description to everyone in /help: the server language's */
    @Test
    void theCommandDescriptionIsInTheServerLanguage() throws Exception {
        MockBukkit.unmock();
        server = TestServer.mock();
        TestServer.start(server, folder -> {
            try (var in = ProstPlugin.class.getResourceAsStream("/config.yml")) {
                String config = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("default: en", "default: de");
                java.nio.file.Files.createDirectories(folder.toPath());
                java.nio.file.Files.writeString(folder.toPath().resolve("config.yml"), config);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
        assertEquals("Prost - das Trinkspiel", server.getCommandMap().getCommand("prost").getDescription());
    }

    /** a plugin that reacts to the service being published may use the API right away */
    @Test
    void theApiIsPublishedOnlyWhenItWorks() {
        MockBukkit.unmock();
        server = TestServer.mock();
        java.util.List<Object> seen = new java.util.ArrayList<>();
        server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void on(org.bukkit.event.server.ServiceRegisterEvent e) {
                if (!(e.getProvider().getProvider() instanceof ProstApi api)) return;
                try {
                    seen.add(api.rounds().size());
                    seen.add(api.roundOf(java.util.UUID.randomUUID()).isPresent());
                } catch (RuntimeException ex) {
                    seen.add(ex);
                }
            }
        }, MockBukkit.createMockPlugin("Watcher"));
        TestServer.start(server);
        assertEquals(List.of(0, false), seen);
    }

    @Test
    void writesItsFiles() {
        for (String f : List.of("config.yml", "game.yml", "challenges.yml", "rules.yml", "lang/en.yml", "lang/de.yml", "stats.db"))
            assertTrue(new File(plugin.getDataFolder(), f).exists(), f);
    }
}
