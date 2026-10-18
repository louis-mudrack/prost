package dev.louismudrack.prost.command;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.ProstApi;
import dev.louismudrack.prost.i18n.Msg;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProstCommandTest {
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host, guest;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = TestServer.start(server);
        host = server.addPlayer("Host");
        guest = server.addPlayer("Guest");
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private List<String> console() { return TestServer.messages(server.getConsoleSender()); }

    private List<String> complete(PlayerMock p, String line) { return server.getCommandMap().tabComplete(p, line); }

    @Test
    void startOpensARound() {
        host.performCommand("prost start party");
        assertNotNull(plugin.rounds().of(host.getUniqueId()));
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("opened")));
    }

    @Test
    void germanAliasesAndTextsThroughTheLanguageProvider() {
        ProstApi api = server.getServicesManager().load(ProstApi.class);
        api.registerLanguageProvider(MockBukkit.createMockPlugin("Network"), p -> Optional.of("de"));
        host.performCommand("prost start");
        TestServer.messages(host);
        host.performCommand("prost einladen Guest");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Einladung an Guest")));
        guest.performCommand("prost join");
        assertSame(plugin.rounds().of(host.getUniqueId()), plugin.rounds().of(guest.getUniqueId()));
    }

    @Test
    void aliasesWorkForEveryLanguage() {
        host.performCommand("prost start");
        TestServer.messages(host);
        host.performCommand("prost einladen Guest");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Invite sent to Guest")));
        guest.performCommand("prost decline");
        assertTrue(TestServer.messages(guest).stream().anyMatch(m -> m.contains("Invite declined")));
    }

    @Test
    void aPlayerPicksALanguage() {
        host.performCommand("prost language de");
        assertEquals("de", Msg.lang(host.getUniqueId()));
        TestServer.messages(host);
        host.performCommand("prost language xx");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("en") && m.contains("de")), "lists what exists");
        host.performCommand("prost language auto");
        assertEquals("en", Msg.lang(host.getUniqueId()));
    }

    @Test
    void aLanguageProviderLocksTheChoice() {
        server.getServicesManager().load(ProstApi.class).registerLanguageProvider(MockBukkit.createMockPlugin("Network"), p -> Optional.of("en"));
        host.performCommand("prost language de");
        assertEquals("en", Msg.lang(host.getUniqueId()));
    }

    @Test
    void oneLanguageForTheServerLocksTheChoiceToo() throws Exception {
        Path config = new File(plugin.getDataFolder(), "config.yml").toPath();
        String yaml = Files.readString(config, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("per-player: true"));
        Files.writeString(config, yaml.replace("per-player: true", "per-player: false"), StandardCharsets.UTF_8);
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        console();
        host.performCommand("prost language de");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("follows the server")));
    }

    @Test
    void theConsoleGetsEnglishAdminReplies() {
        server.dispatchCommand(server.getConsoleSender(), "prost admin list");
        assertEquals(List.of("No round is running."), console());
    }

    @Test
    void reloadWorksAsAdminArgumentAndOnItsOwn() {
        server.dispatchCommand(server.getConsoleSender(), "prost admin reload");
        assertTrue(console().stream().anyMatch(m -> m.startsWith("Reloaded:") && m.contains("storage")));
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        assertTrue(console().stream().anyMatch(m -> m.startsWith("Reloaded:")));
    }

    @Test
    void playersCannotUseTheAdminPart() {
        host.performCommand("prost admin end X");
        host.performCommand("prost reload");
        assertEquals(2, TestServer.messages(host).stream().filter(m -> m.contains("permission")).count());
    }

    @Test
    void adminNamesAreOnlySuggestedToAdmins() {
        assertTrue(complete(host, "prost ").contains("start"));
        assertFalse(complete(host, "prost ").contains("admin"));
        assertFalse(complete(host, "prost ").contains("joker"), "chat-button commands stay hidden");
        assertEquals(List.of(), complete(host, "prost admin "));
        host.setOp(true);
        assertTrue(complete(host, "prost ").contains("admin"));
        assertTrue(complete(host, "prost admin ").containsAll(List.of("reload", "list")));
    }
}
