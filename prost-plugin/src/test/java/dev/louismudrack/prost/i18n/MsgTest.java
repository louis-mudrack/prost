package dev.louismudrack.prost.i18n;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MsgTest {
    private static final UnaryOperator<String> FAKE_PAPI = s -> s.replace("%server_name%", "MyServer").replace("%player_ip%", "1.2.3.4");

    @BeforeEach
    void bind() {
        Lang lang = new Lang(Map.of("en", Map.of("rule.made", "<hl><player> <gray>makes a rule: <white><rule> %server_name%",
                "rule.tight", "<rule><hl>!")), "en", w -> { });
        Msg.bind(lang, null, Theme.DEFAULT, (p, s) -> s);
    }

    @AfterEach
    void unbind() { Msg.bind(Lang.empty(), null, Theme.DEFAULT, (p, s) -> s); }

    private static String plain(String mini) { return PlainTextComponentSerializer.plainText().serialize(Msg.mm(mini)); }

    @Test
    void placeholdersRunOnTheTemplateOnly() {
        String line = Msg.render("en", FAKE_PAPI, "rule.made", V.of("player", "Kasheyy"), V.of("rule", "%player_ip%"));
        assertEquals("Kasheyy makes a rule: %player_ip% MyServer", plain(line));
    }

    @Test
    void playerTextStaysLiteral() {
        String line = Msg.render("en", FAKE_PAPI, "rule.made", V.of("player", "Kasheyy"), V.of("rule", "<red>hi <player> <hl>"));
        assertEquals("Kasheyy makes a rule: <red>hi <player> <hl> MyServer", plain(line));
    }

    @Test
    void typedThemeTagsStayLiteral() {
        String line = Msg.render("en", FAKE_PAPI, "rule.made", V.of("player", "Kasheyy"), V.of("rule", "<hl>x</hl> <primary>y</primary> <muted>"));
        assertEquals("Kasheyy makes a rule: <hl>x</hl> <primary>y</primary> <muted> MyServer", plain(line));
    }

    @Test
    void typedBackslashesRenderAsTheyWereTyped() {
        String line = Msg.render("en", FAKE_PAPI, "rule.made", V.of("player", "Kasheyy"), V.of("rule", "a\\b \\<hl> \\\\"));
        assertEquals("Kasheyy makes a rule: a\\b \\<hl> \\\\ MyServer", plain(line));
    }

    @Test
    void aTrailingBackslashDoesNotEatTheNextTag() {
        assertEquals("end\\!", plain(Msg.render("en", FAKE_PAPI, "rule.tight", V.of("rule", "end\\"))));
        assertEquals("end\\!", plain(Msg.esc("end\\") + "<hl>!"));
    }
}
