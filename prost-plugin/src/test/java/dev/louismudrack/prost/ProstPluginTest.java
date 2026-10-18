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

    @Test
    void writesItsFiles() {
        for (String f : List.of("config.yml", "game.yml", "challenges.yml", "rules.yml", "lang/en.yml", "lang/de.yml", "stats.db"))
            assertTrue(new File(plugin.getDataFolder(), f).exists(), f);
    }
}
