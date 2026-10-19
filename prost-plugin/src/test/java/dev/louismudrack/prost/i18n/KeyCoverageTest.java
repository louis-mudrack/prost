package dev.louismudrack.prost.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every key the code asks for exists in en.yml - a typo would otherwise show the raw key to players. */
class KeyCoverageTest {
    /** one argument before the key (viewer, sender), with at most one level of parentheses: p.getUniqueId() */
    private static final String ARG = "(?:[^\"(),]|\\([^\"()]*\\))+";
    private static final String CALLS = "(?:(?:Msg\\.(?:str|lines|tell|error|round)|\\btext|\\blines|\\breply)\\(\\s*(?:" + ARG + ",\\s*)?"
            + "|new (?:Choice|Game)Menu\\(\\s*" + ARG + ",\\s*" + ARG + ",\\s*)";
    private static final Pattern KEY = Pattern.compile(CALLS + "\"([a-z0-9_]+(?:\\.[a-z0-9_]+)+)\"");
    private static final Pattern BUILT = Pattern.compile(CALLS + "\"([a-z0-9_.]+\\.)\"\\s*\\+");
    /** keys built at runtime: prefix -> every ending that exists */
    static final Map<String, List<String>> EXPANSIONS = Map.of(
            "kind.", List.of("team", "each", "race", "duel", "solo", "reaction", "luck"),
            "pace.", List.of("chill", "normal", "party"),
            "roulette.what.", List.of("break", "place", "craft", "kill", "boss", "eat", "fish", "breed", "trade", "enchant", "advancement", "death", "sleep"),
            "reward.option.", List.of("handout", "rule", "joker", "next"),
            "game.luck.title.", List.of("wheel", "coin", "chests", "dice", "high_low", "boxes"),
            "game.luck.prize.", List.of("drink", "joker", "handout"));

    private static Set<String> english() throws Exception { return LangParityTest.keys(LangParityTest.load("en")); }

    private static List<String> sources() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(p -> p.toString().endsWith(".java")).map(p -> {
                try {
                    return Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toList();
        }
    }

    private static List<String> keysIn(String src) {
        List<String> out = new ArrayList<>();
        Matcher m = KEY.matcher(src);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    void everyCallFormIsRecognised() {
        assertEquals(List.of("a.b", "c.d", "e.f", "g.h", "i.j", "k.l", "m.n", "o.p"), keysIn("""
                Msg.tell(p, "a.b", V.of("x", 1));
                Msg.str(p.getUniqueId(), "c.d");
                reply(s, "e.f");
                new ChoiceMenu(plugin, viewer, "g.h", list);
                text("i.j");
                Msg.round(plugin.rounds().get(id), "k.l");
                lines(viewer, "m.n");
                new GameMenu(plugin, p, "o.p", menu -> draw(menu));
                Msg.tell(p, "Not a key.", "Kein Schlüssel.");
                """));
    }

    @Test
    void everyKeyInTheCodeExistsInEnglish() throws Exception {
        Set<String> en = english(), missing = new TreeSet<>();
        for (String src : sources()) for (String key : keysIn(src)) if (!en.contains(key)) missing.add(key);
        assertEquals(Set.of(), missing);
    }

    @Test
    void everyBuiltKeyIsListedAndExists() throws Exception {
        Set<String> en = english();
        for (String src : sources()) {
            Matcher m = BUILT.matcher(src);
            while (m.find()) assertTrue(EXPANSIONS.containsKey(m.group(1)), "'" + m.group(1) + "' + ... is built at runtime - list its endings in EXPANSIONS");
        }
        EXPANSIONS.forEach((prefix, endings) -> endings.forEach(e -> assertTrue(en.contains(prefix + e), prefix + e + " is missing in en.yml")));
    }
}
