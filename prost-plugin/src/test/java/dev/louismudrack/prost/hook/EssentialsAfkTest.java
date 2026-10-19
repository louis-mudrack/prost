package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.api.AfkProvider;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** EssentialsX decides about AFK only when the plugin named Essentials really is EssentialsX. */
class EssentialsAfkTest {
    private LogCapture log;
    private ServerMock server;

    @BeforeEach
    void mock() {
        server = TestServer.mock();
        log = LogCapture.root();
    }

    @AfterEach
    void unmock() {
        log.close();
        MockBukkit.unmock();
    }

    private List<LogRecord> aboutEssentials() { return log.records().stream().filter(r -> String.valueOf(r.getMessage()).contains("Essentials")).toList(); }

    /** the EssentialsX classes are there (the test classpath has them), but this plugin is not EssentialsX */
    @Test
    void aForeignEssentialsKeepsProstsOwnAfkDetection() {
        MockBukkit.createMockPlugin("Essentials");
        ProstPlugin plugin = TestServer.start(server);
        assertTrue(plugin.isEnabled());
        List<LogRecord> records = aboutEssentials();
        assertEquals(1, records.size(), records.stream().map(LogRecord::getMessage).toList().toString());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("not EssentialsX"), records.get(0).getMessage());

        PlayerMock p = server.addPlayer();
        assertFalse(plugin.hooks().afk(p));
        assertEquals(1, aboutEssentials().size(), "Prost's own detection answers: no failed EssentialsX check");
    }

    /** loads EssentialsAfk on its own and hides EssentialsX's classes, as on a server without EssentialsX */
    private static final class WithoutEssentialsX extends ClassLoader {
        WithoutEssentialsX() { super(EssentialsAfkTest.class.getClassLoader()); }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.earth2me.") || name.startsWith("net.ess3.")) throw new ClassNotFoundException(name);
            if (!name.equals(EssentialsAfk.class.getName())) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) return loaded;
                try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] code = in.readAllBytes();
                    return defineClass(name, code, 0, code.length);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    @Test
    void withoutEssentialsXClassesThereIsNoProvider() throws Exception {
        Plugin named = MockBukkit.createMockPlugin("Essentials");
        Method of = new WithoutEssentialsX().loadClass(EssentialsAfk.class.getName())
                .getMethod("of", Plugin.class, AfkProvider.class, Logger.class);
        assertEquals(Optional.empty(), of.invoke(null, named, (AfkProvider) p -> false, Logger.getLogger("test")));
    }
}
