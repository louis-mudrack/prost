package dev.louismudrack.prost.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Prost's public page is on Hangar and its downloads also on GitHub; the shipped files link nothing else of Prost's. */
class LinksTest {
    private static final String PAGE = "hangar.papermc.io/Kasheyy/Prost";
    private static final Pattern HANGAR = Pattern.compile("hangar\\.papermc\\.io/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+");

    /** each shipped file: its path on the first line, then its content */
    private static List<String> shippedFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main"))) {
            return files.filter(Files::isRegularFile).map(p -> {
                try {
                    return p + "\n" + Files.readString(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).toList();
        }
    }

    private static String path(String file) { return file.lines().findFirst().orElse("?"); }

    @Test
    void everyLinkNamesProstsHangarPage() throws Exception {
        List<String> wrong = new ArrayList<>();
        int links = 0;
        for (String file : shippedFiles()) {
            Matcher m = HANGAR.matcher(file);
            while (m.find()) {
                links++;
                if (!m.group().equals(PAGE)) wrong.add(path(file) + ": " + m.group());
            }
        }
        assertEquals(List.of(), wrong);
        assertTrue(links >= 5, "plugin.yml, config.yml, en.yml, de.yml and the update checker link the page");
    }

    /** with or without https://: a host followed by a path, as the links in the language files are written */
    private static final Pattern LINK = Pattern.compile("(?:https?://)?((?:[a-z0-9-]+\\.)+(?:com|org|io|net|dev|de))/");
    private static final Set<String> HOSTS = Set.of("hangar.papermc.io", "github.com", "api.github.com", "bstats.org",
            "docs.advntr.dev");   // the MiniMessage format, for translators

    /** a draft project or an old page must not be linked: every link goes to a host Prost publishes on */
    @Test
    void everyLinkGoesToAKnownHost() throws Exception {
        List<String> unknown = new ArrayList<>();
        for (String file : shippedFiles()) {
            Matcher m = LINK.matcher(file.toLowerCase(Locale.ROOT));
            while (m.find()) if (!HOSTS.contains(m.group(1))) unknown.add(path(file) + ": " + m.group());
        }
        assertEquals(List.of(), unknown);
    }
}
