package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        assertEquals("vs", plugin.lang().template("en", "challenge.versus"), "the bare word: the code adds the spaces");
        assertEquals("gegen", plugin.lang().template("de", "challenge.versus"));
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

    /** the language files hold the bare word, so a translator cannot lose the spaces around it */
    @Test
    void theRewardsAreJoinedWithTheViewersWord() {
        assertEquals("or", plugin.lang().template("en", "challenge.reward_or"));
        assertEquals("oder", plugin.lang().template("de", "challenge.reward_or"));
        PlayerMock host = server.addPlayer("Host"), guest = server.addPlayer("Guest");
        guest.setLocale(Locale.GERMANY);
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        guest.performCommand("prost join " + r.code);
        TestServer.messages(host);
        TestServer.messages(guest);
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " mine_diamond solo");   // handout or joker
        List<String> english = TestServer.messages(host), german = TestServer.messages(guest);
        assertTrue(english.stream().anyMatch(l -> l.contains(" or Joker")), english.toString());
        assertTrue(german.stream().anyMatch(l -> l.contains(" oder Joker")), german.toString());
    }

    /** a server keeps its own lang/en.yml: the 0.3.0 value " or " must not turn into double spaces */
    @Test
    void anOlderLanguageFileWithTheSpacesInsideReadsTheSame() throws Exception {
        MockBukkit.unmock();
        server = TestServer.mock();
        String shipped;
        try (var in = ProstPlugin.class.getResourceAsStream("/lang/en.yml")) {
            shipped = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String older = shipped.replace("reward_or: \"or\"", "reward_or: \" or \"");
        assertNotEquals(shipped, older);
        plugin = TestServer.start(server, folder -> {
            try {
                Files.createDirectories(folder.toPath().resolve("lang"));
                Files.writeString(folder.toPath().resolve("lang/en.yml"), older, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        PlayerMock host = server.addPlayer("Host");
        host.performCommand("prost start");
        Round r = plugin.rounds().of(host.getUniqueId());
        TestServer.messages(host);
        server.dispatchCommand(server.getConsoleSender(), "prost admin force " + r.code + " mine_diamond solo");
        List<String> english = TestServer.messages(host);
        assertTrue(english.stream().anyMatch(l -> l.contains("sips or Joker")), english.toString());
    }
}
