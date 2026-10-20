package dev.louismudrack.prost.game;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.entity.LivingEntitySimulation;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** returns.yml at the start: an empty file is nobody to bring back, an unreadable one is set aside and never overwritten. */
class ReturnsFileTest {
    private static final String RETURNS = "returns.yml", BROKEN = "players: [unclosed";
    private ServerMock server;
    private ProstPlugin plugin;

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    /** a start with these files in the data folder: what Prost logged about returns.yml */
    private List<String> startWith(Map<String, String> files) {
        server = TestServer.mock();
        try (LogCapture log = LogCapture.root()) {
            plugin = TestServer.start(server, folder -> files.forEach((name, content) -> write(folder.toPath().resolve(name), content)));
            return log.messages().stream().filter(m -> m.contains(RETURNS)).toList();
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String name) { return new File(plugin.getDataFolder(), name).toPath(); }

    private String read(String name) throws IOException { return Files.readString(file(name), StandardCharsets.UTF_8); }

    @Test
    void anUnreadableFileIsSetAsideAndNeverOverwritten() throws IOException {
        List<String> told = startWith(Map.of(RETURNS, BROKEN, RETURNS + ".broken", "an older one"));
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).contains("moved to " + RETURNS + ".broken.2") && told.get(0).contains("rename it to " + RETURNS),
                "where it is and how to use it: " + told);
        assertEquals("an older one", read(RETURNS + ".broken"), "an older copy stays");
        assertEquals(BROKEN, read(RETURNS + ".broken.2"));
        assertFalse(Files.exists(file(RETURNS)));
        assertTrue(plugin.returns().remember(Map.of(UUID.randomUUID(), new Location(server.getWorld("world"), 1, 64, 1))));
        assertTrue(read(RETURNS).contains("world: world"), "the next tour writes a new file");
        assertEquals(BROKEN, read(RETURNS + ".broken.2"), "and leaves the broken one alone");
    }

    @Test
    void aFileThatIsNotAMapIsSetAsideToo() throws IOException {
        List<String> told = startWith(Map.of(RETURNS, "- a list\n"));
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).contains("not a map"), told.toString());
        assertEquals("- a list\n", read(RETURNS + ".broken"));
    }

    @Test
    void anEmptyFileIsNobodyToBringBack() {
        assertEquals(List.of(), startWith(Map.of(RETURNS, "")));
        assertFalse(Files.exists(file(RETURNS + ".broken")));
    }

    private static String wayBack(UUID id) { return id + ":\n  world: world\n  x: 5.5\n  y: 70.0\n  z: 5.5\n  yaw: 30.0\n  pitch: 0.0\n"; }

    @Test
    void aBrokenLandingListIsSkippedAndTheWaysBackLoad() {
        UUID id = UUID.randomUUID();
        List<String> told = startWith(Map.of(RETURNS, wayBack(id) + "landing: nope\n"));
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).contains(RETURNS + ": landing is not a list"), told.toString());
        PlayerMock anna = new PlayerMock(server, "Anna", id);
        server.addPlayer(anna);
        assertEquals(new Location(server.getWorld("world"), 5.5, 70, 5.5, 30f, 0f), anna.getLocation(), "brought back on joining");
        assertFalse(Files.exists(file(RETURNS + ".broken")), "not set aside");
    }

    @Test
    void anIdInTheLandingListThatIsNoPlayerIsSkipped() {
        UUID falling = UUID.randomUUID();
        List<String> told = startWith(Map.of(RETURNS, "landing:\n- not-a-player\n- " + falling + "\n"));
        assertEquals(1, told.size(), told.toString());
        assertTrue(told.get(0).contains("not-a-player"), told.toString());
        PlayerMock ben = new PlayerMock(server, "Ben", falling);
        server.addPlayer(ben);
        assertTrue(new LivingEntitySimulation(ben).simulateDamage(2, DamageSource.builder(DamageType.FALL).build()).isCancelled(),
                "the other landing resumes on joining");
    }
}
