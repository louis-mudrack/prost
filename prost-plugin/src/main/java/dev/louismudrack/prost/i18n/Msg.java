package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.ui.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The game's voice: every text from lang/<code>.yml in the viewer's language. PlaceholderAPI runs on the template,
 * then the theme, then the values - so a value (a name, a typed rule) is never parsed or resolved.
 */
public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static volatile Lang lang = Lang.empty();
    private static volatile LanguageResolver resolver;
    private static volatile Theme theme = Theme.DEFAULT;
    private static volatile BiFunction<Player, String, String> placeholders = (p, s) -> s;

    private Msg() {}

    public static void bind(Lang l, LanguageResolver r, Theme t, BiFunction<Player, String, String> papi) {
        lang = l;
        resolver = r;
        theme = t;
        placeholders = papi;
    }

    public static String lang(UUID viewer) {
        LanguageResolver r = resolver;
        return r == null ? lang.defaultLanguage() : r.of(viewer);
    }

    public static String str(UUID viewer, String key, V... vars) {
        Player p = Bukkit.getPlayer(viewer);
        return render(lang(viewer), s -> p == null ? s : placeholders.apply(p, s), key, vars);
    }

    public static List<String> lines(UUID viewer, String key, V... vars) {
        Player p = Bukkit.getPlayer(viewer);
        return lang.lines(lang(viewer), key).stream()
                .map(line -> V.fill(theme.apply(p == null ? line : placeholders.apply(p, line)), vars)).toList();
    }

    /** the pure core of str(): template -> placeholders -> theme -> values */
    public static String render(String code, UnaryOperator<String> papi, String key, V... vars) {
        return V.fill(theme.apply(papi.apply(lang.template(code, key))), vars);
    }

    public static String sips(UUID viewer, int n) { return str(viewer, n == 1 ? "sips.one" : "sips.other", V.of("n", n)); }

    /** MiniMessage -> Component; theme tags written in code ("<hl>") resolve here too */
    public static Component mm(String miniMessage) { return MM.deserialize(theme.apply(miniMessage)); }

    public static void tell(Player p, String key, V... vars) { send(p, str(p.getUniqueId(), key, vars)); }

    /** a line already put together from str()/sips() pieces, with the chat prefix */
    public static void send(Player p, String line) { p.sendMessage(mm(str(p.getUniqueId(), "prefix") + line)); }

    public static void error(Player p, String key, V... vars) {
        send(p, "<err>" + str(p.getUniqueId(), key, vars));
        Sounds.error(p);
    }

    /** to every online member, each in their own language */
    public static void round(Round r, String key, V... vars) { roundEach(r, id -> str(id, key, vars)); }

    public static void roundEach(Round r, Function<UUID, String> line) {
        for (UUID id : r.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) send(p, line.apply(id));
        }
    }

    /** player-provided text never becomes markup: not a MiniMessage tag, not a value tag, not a theme tag */
    public static String esc(String text) { return V.escape(text); }

    // --- the old DE/EN pairs, only until Task 11 has moved every text into the language files ---

    @Deprecated public static final String PREFIX = "<primary>Prost</primary> <dark_gray>»</dark_gray> <gray>", HL = "<hl>", OK = "<ok>", ERR = "<err>";

    @Deprecated
    public static boolean de(UUID id) { return "de".equals(lang(id)); }

    @Deprecated
    public static String t(UUID id, String de, String en) { return de(id) ? de : en; }

    @Deprecated
    public static void tell(Player p, String de, String en) { send(p, t(p.getUniqueId(), de, en)); }

    @Deprecated
    public static void error(Player p, String de, String en) {
        send(p, "<err>" + t(p.getUniqueId(), de, en));
        Sounds.error(p);
    }

    @Deprecated
    public static void round(Round r, Function<Boolean, String> text) { roundEach(r, id -> text.apply(de(id))); }
}
