package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.i18n.Lang;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.Theme;
import dev.louismudrack.prost.i18n.V;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PlaceholderAPI output is untrusted: nicknames are player-controlled. */
class PapiTextTest {
    private static String plain(String miniMessage) { return PlainTextComponentSerializer.plainText().serialize(Msg.mm(miniMessage)); }

    @Test
    void markupInAValueStaysLiteral() {
        String text = PapiText.substitute("Hi %player_name%!", token -> "<click:run_command:/op x>hi");
        assertEquals("Hi <click:run_command:/op x>hi!", plain(text));
    }

    @Test
    void colourCodesInAValueStillColour() {
        assertEquals("<red>RedX", PapiText.substitute("%luckperms_prefix%X", token -> "&cRed"));
    }

    @Test
    void anUnknownPlaceholderAndPlainPercentStayAsTyped() {
        assertEquals("%nope_x% 100% of 5%", PapiText.substitute("%nope_x% 100% of 5%", token -> token));
    }

    @Test
    void aBackslashInAValueCannotCancelTheEscape() {
        assertEquals("a\\\\\\<red>", PapiText.substitute("%p_x%", token -> "a\\<red>"));
    }

    @Test
    void aValueThatLooksLikeAValueOrThemeTagRendersLiterally() {
        Lang lang = new Lang(Map.of("en", Map.of("line", "<hl><player> <white>%a_b%")), "en", w -> { });
        Msg.bind(lang, null, Theme.DEFAULT, (p, s) -> s);
        try {
            for (String tag : new String[] {"<player>", "<hl>"}) {
                String line = Msg.render("en", text -> PapiText.substitute(text, token -> tag), "line", V.of("player", "Kasheyy"));
                assertEquals("Kasheyy " + tag, plain(line));
            }
        } finally {
            Msg.bind(Lang.empty(), null, Theme.DEFAULT, (p, s) -> s);
        }
    }
}
