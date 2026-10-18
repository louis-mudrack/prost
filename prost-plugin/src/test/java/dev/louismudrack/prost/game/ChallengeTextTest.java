package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** What a challenge says in chat, in each viewer's language. */
class ChallengeTextTest {
    private ServerMock server;
    private ProstPlugin plugin;

    @BeforeEach
    void start() {
        server = TestServer.mock();
        plugin = TestServer.start(server);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private static boolean names(List<String> lines, String a, String word, String b) {
        return lines.stream().anyMatch(l -> l.contains(a + word + b) || l.contains(b + word + a));
    }

    @Test
    void aDuelNamesBothWithTheViewersWordAndTheNamesStayText() {
        PlayerMock host = server.addPlayer("Ho<i>st"), guest = server.addPlayer("Guest");
        guest.setLocale(Locale.GERMANY);
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        guest.performCommand("prost join " + r.code);
        TestServer.messages(host);
        TestServer.messages(guest);
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " jump_200 duel");
        assertNotNull(plugin.director().running(r));
        List<String> english = TestServer.messages(host), german = TestServer.messages(guest);
        assertTrue(names(english, "Ho<i>st", " vs ", "Guest"), english.toString());
        assertTrue(names(german, "Ho<i>st", " gegen ", "Guest"), german.toString());
    }
}
