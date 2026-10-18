package dev.louismudrack.prost.meta;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The slug "prost" on Modrinth is someone else's: links name Prost's page, the API asks by the stable project id. */
class ModrinthLinksTest {
    private static final Pattern LINK = Pattern.compile("modrinth\\.com/plugin/([a-z0-9-]+)");

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

    @Test
    void everyLinkNamesProstsPage() throws Exception {
        List<String> wrong = new ArrayList<>();
        int links = 0;
        for (String file : shippedFiles()) {
            Matcher m = LINK.matcher(file);
            while (m.find()) {
                links++;
                if (!m.group(1).equals("prost-game")) wrong.add(file.lines().findFirst().orElse("?") + ": " + m.group());
            }
        }
        assertEquals(List.of(), wrong);
        assertTrue(links >= 5, "plugin.yml, config.yml, en.yml, de.yml and the update checker link the page");
    }

    @Test
    void theUpdateCheckAsksByProjectId() {
        assertEquals("https://api.modrinth.com/v2/project/q9F9i8rt/version", UpdateChecker.API);
    }
}
