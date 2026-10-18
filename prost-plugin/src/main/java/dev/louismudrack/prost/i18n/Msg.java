package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.hook.Gate;
import dev.louismudrack.prost.ui.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Function;

/** The game's chat voice (UX standard §2): "Prost »" label, MiniMessage only, DE/EN per member, round-only broadcasts. */
public final class Msg {
    public static final String PREFIX = "<gradient:#ffd94d:#ff8f3f>Prost</gradient> <dark_gray>»</dark_gray> <gray>";
    public static final String HL = "<#ffd94d>", OK = "<#55e07a>", ERR = "<#ff5555>";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static boolean de(UUID id) { return "de".equals(Gate.lang(id)); }

    public static String t(UUID id, String de, String en) { return de(id) ? de : en; }

    public static Component mm(String miniMessage) { return MM.deserialize(miniMessage); }

    /** player-provided text (names, own rules) never becomes markup */
    public static String esc(String text) { return MM.escapeTags(text); }

    public static void tell(Player p, String de, String en) { p.sendMessage(mm(PREFIX + t(p.getUniqueId(), de, en))); }

    public static void error(Player p, String de, String en) {
        tell(p, ERR + de, ERR + en);
        Sounds.error(p);
    }

    /** to every online member, each in their own language (de = true for German) */
    public static void round(Round r, Function<Boolean, String> text) {
        for (UUID id : r.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(mm(PREFIX + text.apply(de(id))));
        }
    }
}
