package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/** A server keeps its own language files: a text 0.3.0 shipped and this version changed is renewed, an edited one stays. */
class FormerDefaultsTest {
    /** the update.available texts of 0.3.0, which linked the old project page */
    private static final String EN_0_3_0 = "A new Prost version is out: <hl><version> <gray>- modrinth.com/plugin/prost-game";
    private static final String DE_0_3_0 = "Eine neue Prost-Version ist da: <hl><version> <gray>- modrinth.com/plugin/prost-game";
    private static final String KEY = "update.available";

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    private static String shipped(String lang) {
        try (InputStream in = ProstPlugin.class.getResourceAsStream("/lang/" + lang + ".yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** the shipped file with the given update.available value, as the server's own copy */
    private static void writeWith(File dataFolder, String lang, String value) {
        String shipped = shipped(lang);
        String current = YamlConfiguration.loadConfiguration(new java.io.StringReader(shipped)).getString(KEY);
        String older = shipped.replace("available: \"" + current + "\"", "available: \"" + value + "\"");
        assertNotEquals(shipped, older);
        try {
            Files.createDirectories(dataFolder.toPath().resolve("lang"));
            Files.writeString(dataFolder.toPath().resolve("lang/" + lang + ".yml"), older, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String value(ProstPlugin plugin, String lang) {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/" + lang + ".yml")).getString(KEY);
    }

    private static String shippedValue(String lang) {
        return YamlConfiguration.loadConfiguration(new java.io.StringReader(shipped(lang))).getString(KEY);
    }

    @Test
    void theTextsOf030AreRenewed() {
        ProstPlugin plugin = TestServer.start(TestServer.mock(), folder -> {
            writeWith(folder, "en", EN_0_3_0);
            writeWith(folder, "de", DE_0_3_0);
        });
        assertEquals(shippedValue("en"), value(plugin, "en"));
        assertEquals(shippedValue("de"), value(plugin, "de"));
        assertTrue(value(plugin, "en").contains("https://hangar.papermc.io/Kasheyy/Prost"), value(plugin, "en"));
    }

    @Test
    void anEditedTextStays() {
        String edited = DE_0_3_0.replace("Eine neue", "Achtung, eine neue");
        ProstPlugin plugin = TestServer.start(TestServer.mock(), folder -> writeWith(folder, "de", edited));
        assertEquals(edited, value(plugin, "de"));
    }
}
