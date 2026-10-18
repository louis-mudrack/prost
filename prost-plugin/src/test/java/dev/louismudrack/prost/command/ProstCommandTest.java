package dev.louismudrack.prost.command;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.ProstApi;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ProstCommandTest {
    private ServerMock server;
    private ProstPlugin plugin;
    private PlayerMock host, guest;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
        host = server.addPlayer("Host");
        guest = server.addPlayer("Guest");
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private List<String> console() { return TestServer.messages(server.getConsoleSender()); }

    private List<String> complete(PlayerMock p, String line) { return server.getCommandMap().tabComplete(p, line); }

    /** the commands of every chat button the player was sent since the last call */
    private static List<String> buttons(PlayerMock p) {
        List<String> out = new ArrayList<>();
        for (Component c = p.nextComponentMessage(); c != null; c = p.nextComponentMessage()) collectClicks(c, out);
        return out;
    }

    private static void collectClicks(Component c, List<String> out) {
        if (c.clickEvent() != null && c.clickEvent().payload() instanceof ClickEvent.Payload.Text text) out.add(text.value());
        for (Component child : c.children()) collectClicks(child, out);
    }

    private Path dataFile(String name) { return new File(plugin.getDataFolder(), name).toPath(); }

    private void editConfig(String from, String to) throws Exception {
        String yaml = Files.readString(dataFile("config.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains(from), from);
        Files.writeString(dataFile("config.yml"), yaml.replace(from, to), StandardCharsets.UTF_8);
    }

    private void reload() {
        server.dispatchCommand(server.getConsoleSender(), "prost reload");
        console();
    }

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
    void aliasesWorkForEveryLanguageAndTheInviteButtonsRunRealCommands() {
        host.performCommand("prost start");
        TestServer.messages(host);
        host.performCommand("prost einladen Guest");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("Invite sent to Guest")));
        String code = plugin.rounds().of(host.getUniqueId()).code;
        List<String> invite = buttons(guest);
        assertEquals(List.of("/prost join " + code, "/prost decline"), invite);
        guest.performCommand(invite.get(1).substring(1));
        assertTrue(TestServer.messages(guest).stream().anyMatch(m -> m.contains("Invite declined")));
    }

    @Test
    void theJokerButtonRunsTheJokerAndTheOfferSaysOneSip() {
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        r.member(host.getUniqueId()).jokers = 1;
        TestServer.messages(host);
        plugin.sips().drink(r, host.getUniqueId(), 1, SipLedger.Source.CHALLENGE, id -> "test");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("You would drink 1 sip.")));
        plugin.sips().drink(r, host.getUniqueId(), 2, SipLedger.Source.CHALLENGE, id -> "test");
        List<String> offer = buttons(host);
        assertEquals(List.of("/prost joker"), offer, "a value name typed wrong would leave <cmd> in the click");
        host.performCommand(offer.get(0).substring(1));
        assertEquals(0, r.member(host.getUniqueId()).jokers);
    }

    @Test
    void aPlayerPicksALanguage() {
        host.performCommand("prost language de");
        assertEquals("de", Msg.lang(host.getUniqueId()));
        TestServer.messages(host);
        host.performCommand("prost language xx");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("en") && m.contains("de")), "lists what exists");
        assertEquals("de", Msg.lang(host.getUniqueId()), "an unknown language changes nothing");
        host.performCommand("prost language auto");
        assertEquals("en", Msg.lang(host.getUniqueId()));
    }

    @Test
    void aLanguageProviderLocksTheChoice() {
        server.getServicesManager().load(ProstApi.class).registerLanguageProvider(MockBukkit.createMockPlugin("Network"), p -> Optional.of("en"));
        host.performCommand("prost language de");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("follows the server")));
        assertEquals(Optional.empty(), plugin.prefs().language(host.getUniqueId()), "nothing stored");
    }

    @Test
    void oneLanguageForTheServerLocksTheChoiceToo() throws Exception {
        editConfig("per-player: true", "per-player: false");
        reload();
        host.performCommand("prost language de");
        assertTrue(TestServer.messages(host).stream().anyMatch(m -> m.contains("follows the server")));
        assertEquals(Optional.empty(), plugin.prefs().language(host.getUniqueId()), "nothing stored");
    }

    @Test
    void theServerLanguageWinsANameClash() throws Exception {
        Files.writeString(dataFile("lang/fr.yml"), "commands:\n  leave: [einladen]\n", StandardCharsets.UTF_8);
        editConfig("default: en", "default: fr");
        reload();
        host.performCommand("prost start");
        host.performCommand("prost einladen");
        assertNull(plugin.rounds().of(host.getUniqueId()), "einladen means leave on a French server, de.yml's invite is skipped");
    }

    @Test
    void theConsoleGetsEnglishAdminReplies() {
        server.dispatchCommand(server.getConsoleSender(), "prost admin list");
        assertEquals(List.of("No round is running."), console());
    }

    @Test
    void reloadWorksAsAdminArgumentAndOnItsOwn() {
        server.dispatchCommand(server.getConsoleSender(), "prost admin reload");
        assertTrue(console().stream().anyMatch(m -> m.startsWith("Reloaded:") && m.contains("storage") && m.contains("commands.aliases")));
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

    @Test
    void executeAsAPlayerPlaysForThemAndAnyOtherEntityIsRefused() {
        Location at = host.getLocation();
        plugin.command().execute(new CommandSourceStackMock(at, server.getConsoleSender(), host), new String[]{"start"});
        assertNotNull(plugin.rounds().of(host.getUniqueId()));
        Zombie zombie = at.getWorld().spawn(at, Zombie.class);
        plugin.command().execute(new CommandSourceStackMock(at, guest, zombie), new String[]{"start"});
        assertNull(plugin.rounds().of(guest.getUniqueId()), "the zombie runs it, not the player who typed /execute");
        assertTrue(TestServer.messages(guest).stream().anyMatch(m -> m.contains("Only a player can do that")));
        guest.setLocale(java.util.Locale.GERMANY);
        plugin.command().execute(new CommandSourceStackMock(at, guest, zombie), new String[]{"start"});
        assertTrue(TestServer.messages(guest).stream().anyMatch(m -> m.contains("Das kann nur ein Spieler")), "the player's language");
        plugin.command().execute(new CommandSourceStackMock(at, server.getConsoleSender(), zombie), new String[]{"start"});
        assertTrue(console().stream().anyMatch(m -> m.contains("Only a player can do that")), "the console in English");
    }
}
