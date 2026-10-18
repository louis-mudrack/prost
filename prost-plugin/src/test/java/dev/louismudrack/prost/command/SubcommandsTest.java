package dev.louismudrack.prost.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SubcommandsTest {
    /** a command the code runs or puts on a button: "/prost reward", "prost pause round", "prost start " + pace */
    private static final Pattern RUN = Pattern.compile("\"/?prost ([a-z]+)((?: [a-z]+)*)");
    private static final Pattern LITERAL_BUTTON = Pattern.compile("run_command:'(?!<[a-z_]+>')[^']*'");

    private final List<String> warnings = new ArrayList<>();

    private Subcommands subs(Map<String, Map<String, List<String>>> aliases) { return Subcommands.of(aliases, warnings::add); }

    @Test
    void englishNamesAlwaysWork() {
        Subcommands s = subs(Map.of());
        assertEquals("invite", s.canonical("INVITE"));
        assertEquals("nonsense", s.canonical("nonsense"));
    }

    @Test
    void languageAliasesWorkForEveryone() {
        Subcommands s = subs(Map.of("de", Map.of("invite", List.of("einladen"), "round", List.of("runde"))));
        assertEquals("invite", s.canonical("einladen"));
        assertEquals("round", s.canonical("Runde"));
    }

    @Test
    void collisionsAndUnknownSubcommandsAreSkipped() {
        Subcommands s = subs(Map.of("de", Map.of("invite", List.of("start"), "fly", List.of("fliegen"))));
        assertEquals("start", s.canonical("start"));
        assertEquals("fliegen", s.canonical("fliegen"));
        assertEquals(2, warnings.size());
    }

    @Test
    void blankAndMultiWordAliasesAreSkippedWithAWarning() {
        Subcommands s = subs(Map.of("de", Map.of("invite", List.of("", "  ", "lade ein", "einladen"))));
        assertEquals(3, warnings.size(), warnings.toString());
        assertEquals(List.of("invite", "einladen"), s.namesOf("invite", "de"));
        assertEquals("lade ein", s.canonical("lade ein"));
    }

    @Test
    void anAliasThatAlreadyNamesTheSameSubcommandIsSkippedQuietly() {
        Subcommands s = subs(Map.of("de", Map.of("invite", List.of("invite", "einladen", " Einladen "))));
        assertEquals(List.of(), warnings);
        assertEquals(List.of("invite", "einladen"), s.namesOf("invite", "de"));
        assertEquals(1, s.visible("de", false).stream().filter("einladen"::equals).count());
    }

    @Test
    void theLanguageThatComesFirstKeepsAClashedName() {
        Map<String, Map<String, List<String>>> frFirst = new LinkedHashMap<>();
        frFirst.put("fr", Map.of("leave", List.of("x")));
        frFirst.put("de", Map.of("invite", List.of("x")));
        assertEquals("leave", subs(frFirst).canonical("x"));
        Map<String, Map<String, List<String>>> deFirst = new LinkedHashMap<>();
        deFirst.put("de", Map.of("invite", List.of("x")));
        deFirst.put("fr", Map.of("leave", List.of("x")));
        assertEquals("invite", subs(deFirst).canonical("x"));
        assertEquals(2, warnings.size());
    }

    @Test
    void tabCompletionShowsEnglishPlusTheOwnLanguage() {
        Subcommands s = subs(Map.of("de", Map.of("invite", List.of("einladen"))));
        assertTrue(s.visible("de", false).containsAll(List.of("invite", "einladen")));
        assertFalse(s.visible("en", false).contains("einladen"));
        assertFalse(s.visible("de", false).contains("admin"));
        assertTrue(s.visible("de", true).contains("admin"));
        assertFalse(s.visible("de", false).contains("joker"), "chat-button commands stay hidden");
        assertEquals(List.of("round", "runde"), subs(Map.of("de", Map.of("round", List.of("runde")))).namesOf("round", "de"));
    }

    /** without any language file: a German alias in a button or menu would stop working once lang/de.yml is gone */
    @Test
    void everyCommandTheCodeRunsIsARealEnglishSubcommand() throws IOException {
        Subcommands english = subs(Map.of());
        Set<String> real = Stream.of(Subcommands.PLAYER, Subcommands.HIDDEN, Subcommands.ADMIN).flatMap(List::stream).collect(Collectors.toSet());
        List<String> found = new ArrayList<>();
        for (String src : sources()) {
            Matcher m = RUN.matcher(src);
            while (m.find()) {
                String sub = english.canonical(m.group(1));
                found.add(sub);
                assertTrue(real.contains(sub), "/prost " + m.group(1) + " is not a subcommand");
                if (sub.equals("pause") && !m.group(2).isBlank()) assertEquals("round", english.canonical(m.group(2)), "/prost pause" + m.group(2));
            }
        }
        assertTrue(found.containsAll(List.of("reward", "handout", "joker", "decline", "join", "leave", "next", "pace", "end", "kick", "start")), found.toString());
    }

    @Test
    void chatButtonsTakeTheirCommandFromCode() throws IOException {
        for (String code : List.of("en", "de")) {
            String file = Files.readString(Path.of("src/main/resources/lang/" + code + ".yml"), StandardCharsets.UTF_8);
            Matcher m = LITERAL_BUTTON.matcher(file);
            assertFalse(m.find(), () -> "lang/" + code + ".yml: " + m.group() + " - fill the command from code (<cmd>)");
        }
    }

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
}
