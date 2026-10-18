package dev.louismudrack.prost.i18n;

import dev.louismudrack.prost.hook.Hooks;
import dev.louismudrack.prost.storage.PlayerPrefs;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** The chain on a server: shipped files written and read, the jar's English behind them, the resolver bound into Msg. */
class LanguageBootTest {
    private ServerMock server;
    private JavaPlugin plugin;

    @BeforeEach
    void start() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("Prost");
    }

    @AfterEach
    void stop() {
        Msg.bind(Lang.empty(), null, Theme.DEFAULT, (p, s) -> s);
        MockBukkit.unmock();
    }

    @Test
    void theShippedLanguagesAreWrittenAndRead() {
        Lang lang = LangFiles.load(plugin, "de");
        assertTrue(new File(plugin.getDataFolder(), "lang/en.yml").isFile());
        assertTrue(new File(plugin.getDataFolder(), "lang/de.yml").isFile());
        assertEquals(Set.of("en", "de"), lang.languages());
        assertEquals("de", lang.defaultLanguage());
        assertEquals("<n> Schluck", lang.template("de", "sips.one"));
    }

    @Test
    void aBrokenEnglishFileStillAnswersWithTheShippedEnglishAndWarnsOnce() throws Exception {
        LangFiles.load(plugin, "en");
        Files.writeString(new File(plugin.getDataFolder(), "lang/en.yml").toPath(), "sips: [unclosed\n", StandardCharsets.UTF_8);
        List<String> warnings = new ArrayList<>();
        plugin.getLogger().addHandler(new Handler() {
            @Override public void publish(LogRecord r) { if (r.getLevel() == Level.WARNING) warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        Lang lang = LangFiles.load(plugin, "en");
        assertEquals("<n> sips", lang.template("en", "sips.other"));
        assertEquals(1, warnings.size(), warnings.toString());
    }

    @Test
    void aGermanClientSeesGermanAndAProviderOverridesIt() {
        Lang lang = LangFiles.load(plugin, "en");
        Hooks hooks = new Hooks(Logger.getAnonymousLogger(), p -> false, env -> true, l -> false);
        Msg.bind(lang, new LanguageResolver(lang, hooks, new PlayerPrefs(null), true), Theme.DEFAULT, (p, s) -> s);
        PlayerMock p = server.addPlayer("Kasheyy");
        p.setLocale(Locale.GERMANY);
        assertEquals("de", Msg.lang(p.getUniqueId()));
        assertEquals("3 Schlucke", Msg.sips(p.getUniqueId(), 3));
        hooks.setLanguage(MockBukkit.createMockPlugin("Other"), player -> Optional.of("EN-us"));
        assertEquals("en", Msg.lang(p.getUniqueId()));
        assertEquals("1 sip", Msg.sips(p.getUniqueId(), 1));
    }
}
