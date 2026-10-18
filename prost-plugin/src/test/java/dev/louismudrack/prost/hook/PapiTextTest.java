package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.i18n.Msg;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

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
}
