package dev.louismudrack.prost.drink;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The reason after a drink ("from Alex", "Roulette: ...") is already a rendered text: inserted once, not escaped again. */
class ReasonTextTest {
    private ServerMock server;

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private ProstPlugin startWithStyledHandoutFrom() {
        server = TestServer.mock();
        return TestServer.start(server, folder -> {
            try {
                Path lang = Files.createDirectories(folder.toPath().resolve("lang"));
                Files.writeString(lang.resolve("en.yml"), "handout:\n  from: \"from <red><player></red>\"\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Test
    void aStyledReasonShowsItsColourAndANameInsideStaysText() {
        ProstPlugin plugin = startWithStyledHandoutFrom();
        PlayerMock p = server.addPlayer("Host");
        p.performCommand("prost start");
        Round r = plugin.rounds().of(p.getUniqueId());
        while (p.nextComponentMessage() != null) { }
        plugin.sips().drink(r, p.getUniqueId(), 2, SipLedger.Source.HANDOUT, viewer -> Msg.str(viewer, "handout.from", V.of("player", "Ev<i>l")));
        Component line = null;
        for (Component c = p.nextComponentMessage(); c != null; c = p.nextComponentMessage())
            if (PlainTextComponentSerializer.plainText().serialize(c).contains("from")) line = c;
        assertNotNull(line);
        String plain = PlainTextComponentSerializer.plainText().serialize(line);
        assertTrue(plain.contains("from Ev<i>l"), plain);
        assertFalse(plain.contains("<red>"), plain);
        assertTrue(GsonComponentSerializer.gson().serialize(line).contains("\"color\":\"red\""), GsonComponentSerializer.gson().serialize(line));
    }
}
