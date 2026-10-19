package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.LogCapture;
import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.TestServer;
import dev.louismudrack.prost.migration.OldInstall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * challenges.yml on an update: the entries a version adds are appended once, as text below the admin's file, which stays
 * byte for byte as it was; an entry deleted after that stays deleted.
 */
class CatalogueMergeTest {
    private static final String FILE = "challenges.yml", MARKER = ".catalogue-version";
    /** spec §8: the entries Prost 0.4.0 added */
    private static final int ADDED_IN_040 = 26;
    private static final String BOM = "﻿";
    private ServerMock server;
    private LogCapture log;

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

    private static String resource(String name) {
        try (InputStream in = CatalogueMergeTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** challenges.yml exactly as Prost 0.3.1 wrote it */
    private static String old() { return resource("/catalogue/challenges-0.3.1.yml"); }

    private static String shipped() { return resource("/challenges.yml"); }

    /** the shipped lines of the entries 0.4.0 added, in file order */
    private static List<String> newLines() { return shipped().lines().filter(l -> l.endsWith("since: \"0.4.0\"}")).toList(); }

    private static List<String> newLinesBut(String... ids) {
        return newLines().stream().filter(l -> Stream.of(ids).noneMatch(id -> l.startsWith(id + ":"))).toList();
    }

    /** what the merge appends: a blank line, the comment, the lines */
    private static String block(String newline, List<String> lines) {
        return newline + "# added by Prost " + TestServer.version() + newline
                + lines.stream().map(l -> l + newline).collect(Collectors.joining());
    }

    private static String block(List<String> lines) { return block("\n", lines); }

    private static String line(String file, String id) { return file.lines().filter(l -> l.startsWith(id + ":")).findFirst().orElseThrow(); }

    /** the file with the line of that entry changed */
    private static String editLine(String file, String id, String from, String to) {
        String line = line(file, id), edited = line.replace(from, to);
        assertNotEquals(line, edited, from);
        return file.replace(line, edited);
    }

    private static String withoutLine(String file, String id) { return file.replace(line(file, id) + "\n", ""); }

    private static void write(Path file, byte[] content) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) { write(file, content.getBytes(StandardCharsets.UTF_8)); }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) { return new String(bytes(file), StandardCharsets.UTF_8); }

    private ProstPlugin start(byte[] challenges) { return TestServer.start(server, folder -> write(folder.toPath().resolve(FILE), challenges)); }

    private ProstPlugin start(String challenges) { return start(challenges.getBytes(StandardCharsets.UTF_8)); }

    private static Path file(ProstPlugin plugin) { return plugin.getDataFolder().toPath().resolve(FILE); }

    private static Path marker(ProstPlugin plugin) { return plugin.getDataFolder().toPath().resolve(MARKER); }

    private void reload() { server.dispatchCommand(server.getConsoleSender(), "prost reload"); }

    private List<String> added() { return log.messages().stream().filter(m -> m.contains("new challenges added")).toList(); }

    private List<LogRecord> warnings() {
        return log.records().stream().filter(r -> r.getLevel() == Level.WARNING && r.getMessage().startsWith(FILE)).toList();
    }

    private static ChallengeDef find(ProstPlugin plugin, String id) { return Catalogue.find(plugin.challenges(), id); }

    @Test
    void anOldFileGetsTheNewEntriesOnceKeepingItsEdits() {
        String edited = withoutLine(editLine(old(), "mine_iron", "sips: 3", "sips: 5"), "walk_km");
        ProstPlugin plugin = start(edited);
        assertEquals(ADDED_IN_040, newLines().size());
        assertEquals(edited + block(newLines()), read(file(plugin)));
        assertEquals(5, find(plugin, "mine_iron").sips());
        assertNull(find(plugin, "walk_km"));
        assertNotNull(find(plugin, "hot_potato"));
        assertEquals(128, plugin.challenges().size());
        assertEquals(List.of("challenges.yml: 26 new challenges added (Prost " + TestServer.version() + ")"), added());
        assertEquals(TestServer.version(), read(marker(plugin)).trim());

        byte[] merged = bytes(file(plugin));
        assertEquals(0, CatalogueMerge.ensure(plugin));
        reload();
        assertArrayEquals(merged, bytes(file(plugin)), "a reload appends nothing");
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().clearPlugins();
        ProstPlugin again = MockBukkit.load(ProstPlugin.class);
        assertArrayEquals(merged, bytes(file(again)), "a second start appends nothing");
        assertEquals(128, again.challenges().size());
        assertEquals(1, added().size());
    }

    @Test
    void anEntryDeletedAfterTheMergeStaysDeleted() {
        ProstPlugin plugin = start(old());
        String withoutWheel = withoutLine(read(file(plugin)), "wheel");
        write(file(plugin), withoutWheel);
        reload();
        assertEquals(withoutWheel, read(file(plugin)));
        assertNull(find(plugin, "wheel"));
        assertEquals(128, plugin.challenges().size());
    }

    @Test
    void aFreshInstallGetsTheWholeFileAndAMarker() {
        ProstPlugin plugin = TestServer.start(server);
        assertEquals(shipped(), read(file(plugin)));
        assertEquals(129, plugin.challenges().size());
        assertEquals(TestServer.version(), read(marker(plugin)).trim());
        assertEquals(List.of(), added());
    }

    static Stream<Arguments> admins() {
        String lf = old(), crlf = lf.replace("\n", "\r\n");
        String noNewline = lf.substring(0, lf.length() - 1), crlfNoNewline = crlf.substring(0, crlf.length() - 2);
        return Stream.of(
                Arguments.of("as 0.3.1 wrote it", lf, lf + block(newLines()), 129),
                Arguments.of("CRLF line endings", crlf, crlf + block("\r\n", newLines()), 129),
                Arguments.of("a byte order mark", BOM + lf, BOM + lf + block(newLines()), 129),
                Arguments.of("no newline at the end", noNewline, noNewline + "\n" + block(newLines()), 129),
                Arguments.of("CRLF and no newline at the end", crlfNoNewline, crlfNoNewline + "\r\n" + block("\r\n", newLines()), 129),
                Arguments.of("comments only", "# all mine\n\n", "# all mine\n\n" + block(newLines()), ADDED_IN_040),
                Arguments.of("empty", "", block(newLines()), ADDED_IN_040));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("admins")
    void theAdminsBytesStayAboveTheNewEntries(String what, String admins, String merged, int challenges) {
        ProstPlugin plugin = start(admins);
        assertEquals(merged, read(file(plugin)));
        assertEquals(challenges, plugin.challenges().size());
        assertEquals(List.of(), warnings());
    }

    static Stream<Arguments> unreadable() {
        return Stream.of(
                Arguments.of("a YAML error", (old() + "broken: [unclosed\n").getBytes(StandardCharsets.UTF_8)),
                Arguments.of("a duplicate id", (old() + line(old(), "mine_iron") + "\n").getBytes(StandardCharsets.UTF_8)),
                Arguments.of("not UTF-8", (old() + "# Grüße\n").getBytes(StandardCharsets.ISO_8859_1)),
                Arguments.of("a list, not a map of challenges", "- mine_iron\n- walk_km\n".getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unreadable")
    void anUnreadableFileIsLeftAlone(String what, byte[] content) {
        ProstPlugin plugin = start(content);
        assertArrayEquals(content, bytes(file(plugin)));
        assertEquals(1, warnings().size(), warnings().stream().map(LogRecord::getMessage).toList().toString());
        assertFalse(Files.exists(marker(plugin)));
        assertEquals(List.of(), added());

        write(file(plugin), old());
        reload();
        assertEquals(old() + block(newLines()), read(file(plugin)), "fixed: the new entries follow");
    }

    /** a closing "..." would make them a second document; a |+ text would take the blank line above them into its value */
    static Stream<Arguments> endings() {
        return Stream.of(
                Arguments.of("a document end", old() + "...\n"),
                Arguments.of("a text that keeps its line breaks", old() + "note:\n  name: {en: Note}\n  tracker: stat\n  stat: NOTEBLOCK_PLAYED\n"
                        + "  seconds: 60\n  sips: 1\n  kinds: [each]\n  rewards: [handout]\n  text:\n    en: |+\n      Play a note block\n\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endings")
    void aFileWhoseEndAnAppendWouldChangeIsLeftAlone(String what, String admins) {
        ProstPlugin plugin = start(admins);
        assertEquals(admins, read(file(plugin)));
        assertEquals(1, warnings().size(), warnings().stream().map(LogRecord::getMessage).toList().toString());
        assertFalse(Files.exists(marker(plugin)));
    }

    @Test
    void anAdminEntryWithANewIdIsNotOverwritten() {
        String mine = old() + "wheel: {name: {en: \"My wheel\"}, text: {en: \"Jump {amount} times\"}, tracker: stat, stat: JUMP, "
                + "amount: 3, seconds: 60, sips: 1, kinds: [each], rewards: [handout]}\n"
                + "Dice: {name: {en: \"My dice\"}, text: {en: \"Jump {amount} times\"}, tracker: stat, stat: JUMP, "
                + "amount: 6, seconds: 60, sips: 1, kinds: [each], rewards: [handout]}\n";
        ProstPlugin plugin = start(mine);
        assertEquals(mine + block(newLinesBut("wheel", "dice")), read(file(plugin)));
        assertEquals("My wheel", find(plugin, "wheel").name("en"));
        assertEquals("My dice", find(plugin, "dice").name("en"), "ids are not case-sensitive");
        assertEquals(List.of("challenges.yml: 24 new challenges added (Prost " + TestServer.version() + ")"), added());
    }

    @Test
    void aFailedWriteIsTriedAgain() {
        ProstPlugin plugin = TestServer.start(server, folder -> {
            write(folder.toPath().resolve(FILE), old());
            write(folder.toPath().resolve(FILE + ".tmp").resolve("in-the-way"), "");   // the temp file cannot be written
        });
        assertEquals(old(), read(file(plugin)));
        assertEquals(1, warnings().size(), warnings().stream().map(LogRecord::getMessage).toList().toString());
        assertFalse(Files.exists(marker(plugin)));

        clearTemp(plugin, FILE + ".tmp");
        reload();
        assertEquals(old() + block(newLines()), read(file(plugin)));
        assertEquals(TestServer.version(), read(marker(plugin)).trim());
    }

    @Test
    void aMarkerThatCannotBeSavedNeverDoublesAnEntry() {
        ProstPlugin plugin = TestServer.start(server, folder -> {
            write(folder.toPath().resolve(FILE), old());
            write(folder.toPath().resolve(MARKER + ".tmp").resolve("in-the-way"), "");
        });
        String merged = read(file(plugin));
        assertEquals(old() + block(newLines()), merged);
        assertTrue(warnings().isEmpty(), "the file was written");
        assertTrue(log.records().stream().anyMatch(r -> r.getLevel() == Level.WARNING && r.getMessage().startsWith(MARKER)));
        assertEquals(0, CatalogueMerge.ensure(plugin));
        assertEquals(merged, read(file(plugin)));
    }

    private static void clearTemp(ProstPlugin plugin, String name) {
        Path tmp = plugin.getDataFolder().toPath().resolve(name);
        try {
            Files.delete(tmp.resolve("in-the-way"));
            Files.delete(tmp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aNewerVersionsMarkerIsLeftAsItIs() {
        ProstPlugin plugin = TestServer.start(server, folder -> {
            write(folder.toPath().resolve(FILE), old());
            write(folder.toPath().resolve(MARKER), "9.0.0\n");
        });
        assertEquals(old(), read(file(plugin)), "a downgrade appends nothing");
        assertEquals("9.0.0\n", read(marker(plugin)));
        assertEquals(List.of(), added());
    }

    /** Prost had written its shipped file and the marker when AniDrinks' folder came: its catalogue has none of the new entries */
    @Test
    void aTakenOverAniDrinksFileGetsTheNewEntries() throws Exception {
        ProstPlugin first = TestServer.start(server);
        Path data = first.getDataFolder().toPath();
        assertTrue(Files.exists(marker(first)));
        server.getPluginManager().disablePlugin(first);
        Path aniDrinks = OldInstall.write(data.getParent());
        String theirs = editLine(old(), "mine_iron", "sips: 3", "sips: 4");
        write(aniDrinks.resolve(FILE), theirs);

        server.getPluginManager().clearPlugins();
        ProstPlugin plugin = MockBukkit.load(ProstPlugin.class);
        assertEquals(data, plugin.getDataFolder().toPath());
        assertTrue(Files.isDirectory(data.getParent().resolve("AniDrinks.migrated")));
        assertEquals(theirs + block(newLines()), read(data.resolve(FILE)));
        assertEquals(4, find(plugin, "mine_iron").sips());
        assertEquals(129, plugin.challenges().size());
        assertEquals(TestServer.version(), read(data.resolve(MARKER)).trim());
    }
}
