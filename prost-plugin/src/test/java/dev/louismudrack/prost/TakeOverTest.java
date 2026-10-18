package dev.louismudrack.prost;

import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.migration.AniDrinksMigration.Outcome;
import dev.louismudrack.prost.migration.OldInstall;
import dev.louismudrack.prost.storage.StatsRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static dev.louismudrack.prost.migration.OldInstall.KASHEYY;
import static org.junit.jupiter.api.Assertions.*;

/** Taking over plugins/AniDrinks on a running server: at the first start and with /prost admin migrate. */
class TakeOverTest {
    private ServerMock server;

    @BeforeEach
    void mock() { server = TestServer.mock(); }

    @AfterEach
    void unmock() { MockBukkit.unmock(); }

    private static Path pluginsDir(ProstPlugin plugin) { return plugin.getDataFolder().getParentFile().toPath(); }

    private String migrate() {
        TestServer.messages(server.getConsoleSender());
        server.dispatchCommand(server.getConsoleSender(), "prost admin migrate");
        return String.join("\n", TestServer.messages(server.getConsoleSender()));
    }

    private static int drunk(ProstPlugin plugin, UUID id) throws Exception {
        return plugin.stats().fetch(id).get(5, TimeUnit.SECONDS).orElseThrow().drunk();
    }

    private static void prostAlreadyHasStats(ProstPlugin plugin) throws Exception {
        plugin.stats().importRow(new StatsRow(KASHEYY, "Kasheyy", 3, 0, 0, 0, 0, 0, 0, 0, 1), 1).get(5, TimeUnit.SECONDS);
    }

    @Test
    void theFirstStartTakesAniDrinksOverBeforeReadingItsFiles() throws Exception {
        Path plugins = server.getPluginManager().getParentTemporaryDirectory().toPath();
        OldInstall.write(plugins);
        ProstPlugin plugin = TestServer.start(server);
        assertEquals(plugins, pluginsDir(plugin));
        assertFalse(Files.exists(plugins.resolve("AniDrinks")));
        assertTrue(Files.isDirectory(plugins.resolve("AniDrinks.migrated")));
        assertEquals(6, plugin.cfg().maxPlayers(), "AniDrinks' drinks.yml, not the shipped game.yml");
        assertEquals(26, drunk(plugin, KASHEYY));
        assertTrue(migrate().contains("There is no AniDrinks folder"), "a second run finds nothing");
    }

    @Test
    void aStartLeavesAniDrinksAloneWhenProstHasStats() throws Exception {
        ProstPlugin plugin = TestServer.start(server);
        prostAlreadyHasStats(plugin);
        Path old = OldInstall.write(pluginsDir(plugin));
        assertEquals(Outcome.BLOCKED, plugin.migrateOnStart());
        assertTrue(Files.isRegularFile(old.resolve("stats.db")));
        assertEquals(3, drunk(plugin, KASHEYY), "Prost's own numbers are not replaced");
    }

    @Test
    void adminMigrateTakesOverEvenWhenProstHasStats() throws Exception {
        ProstPlugin plugin = TestServer.start(server);
        prostAlreadyHasStats(plugin);
        OldInstall.write(pluginsDir(plugin));
        assertTrue(migrate().contains("AniDrinks data taken over"));
        assertTrue(Files.isDirectory(pluginsDir(plugin).resolve("AniDrinks.migrated")));
        assertEquals(26, drunk(plugin, KASHEYY));
    }

    @Test
    void adminMigrateLoadsAniDrinksRoundsRightAway() throws Exception {
        ProstPlugin plugin = TestServer.start(server);
        assertFalse(new File(plugin.getDataFolder(), "rounds.yml").exists(), "Prost has not run a round yet");
        Path old = OldInstall.write(pluginsDir(plugin));
        OldInstall.runningRound(old);
        assertTrue(migrate().contains("AniDrinks data taken over"));
        assertRoundRestored(plugin);
    }

    /** a copy left by a failed attempt is loaded by the next one, before a stop could save the empty list over it */
    @Test
    void aRetryAfterAFailedCommandStillLoadsTheRounds() throws Exception {
        ProstPlugin plugin = TestServer.start(server);
        Path old = OldInstall.write(pluginsDir(plugin));
        OldInstall.runningRound(old);
        OldInstall.breakRounds(old);
        assertTrue(migrate().contains("failed"));
        OldInstall.repairRounds(old);
        assertTrue(migrate().contains("AniDrinks data taken over"));
        assertRoundRestored(plugin);
    }

    /** a start that took nothing over wrote Prost's shipped files and, at its stop, an empty rounds.yml: none of it counts as Prost's own */
    @Test
    void aBlockedStartLeavesNothingInTheWayOfTheNext() throws Exception {
        Path plugins = server.getPluginManager().getParentTemporaryDirectory().toPath();
        Path old = OldInstall.write(plugins);
        OldInstall.runningRound(old);
        MockBukkit.createMockPlugin("AniDrinks");
        ProstPlugin blocked = TestServer.start(server);
        File data = blocked.getDataFolder();
        assertTrue(Files.isRegularFile(old.resolve("stats.db")), "AniDrinks.jar is still there: nothing taken over");
        assertTrue(new File(data, "game.yml").exists());
        server.getPluginManager().disablePlugin(blocked);
        assertEquals("[]", Files.readString(new File(data, "rounds.yml").toPath()).trim());

        server.getPluginManager().clearPlugins();   // AniDrinks.jar removed, Prost starts again
        ProstPlugin plugin = MockBukkit.load(ProstPlugin.class);
        assertEquals(data, plugin.getDataFolder());
        assertTrue(Files.isDirectory(plugins.resolve("AniDrinks.migrated")));
        assertEquals(6, plugin.cfg().maxPlayers());
        assertEquals("mine_iron: {}\n", Files.readString(new File(data, "challenges.yml").toPath()));
        assertEquals("- {de: a, en: b}\n", Files.readString(new File(data, "rules.yml").toPath()));
        assertRoundRestored(plugin);
    }

    private static void assertRoundRestored(ProstPlugin plugin) {
        Round r = plugin.rounds().byCode("L9D5");
        assertNotNull(r);
        assertEquals(r, plugin.rounds().of(KASHEYY));
        Member kasheyy = r.member(KASHEYY);
        assertEquals(12, kasheyy.limit);
        assertTrue(kasheyy.water);
        assertEquals(List.of(new Rule(Map.of("de", "Nur links trinken", "en", "Drink left-handed"))), r.rules);
    }

    @Test
    void anInstalledAniDrinksBlocksTheTakeOver() throws Exception {
        ProstPlugin plugin = TestServer.start(server);
        MockBukkit.createMockPlugin("AniDrinks");
        Path old = OldInstall.write(pluginsDir(plugin));
        assertTrue(migrate().contains("AniDrinks is still installed"));
        assertTrue(Files.isRegularFile(old.resolve("stats.db")));
        assertFalse(Files.exists(pluginsDir(plugin).resolve("AniDrinks.migrated")));
    }

    @Test
    void withoutAniDrinksThereIsNothingToTakeOver() {
        TestServer.start(server);
        assertTrue(migrate().contains("There is no AniDrinks folder"));
    }

    @Test
    void aFailedTakeOverSaysSoAndKeepsTheFolder() throws Exception {
        ProstPlugin plugin = TestServer.start(server);
        Path old = OldInstall.write(pluginsDir(plugin));
        OldInstall.breakRounds(old);
        assertTrue(migrate().contains("failed"));
        assertTrue(Files.isRegularFile(old.resolve("stats.db")));
        assertFalse(Files.exists(pluginsDir(plugin).resolve("AniDrinks.migrated")));
    }

    @Test
    void theAdminUsageListsMigrate() {
        TestServer.start(server);
        server.dispatchCommand(server.getConsoleSender(), "prost admin nonsense");
        List<String> reply = TestServer.messages(server.getConsoleSender());
        assertTrue(reply.stream().anyMatch(m -> m.contains("| migrate")), reply.toString());
    }
}
