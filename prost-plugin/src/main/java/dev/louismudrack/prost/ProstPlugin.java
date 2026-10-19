package dev.louismudrack.prost;

import dev.louismudrack.prost.catalogue.Catalogue;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.catalogue.RulesFile;
import dev.louismudrack.prost.catalogue.Texts;
import dev.louismudrack.prost.command.ProstCommand;
import dev.louismudrack.prost.command.Subcommands;
import dev.louismudrack.prost.config.DataFiles;
import dev.louismudrack.prost.config.GameConfig;
import dev.louismudrack.prost.config.PluginConfig;
import dev.louismudrack.prost.config.YamlMerge;
import dev.louismudrack.prost.drink.Sips;
import dev.louismudrack.prost.api.AfkProvider;
import dev.louismudrack.prost.api.ProstApi;
import dev.louismudrack.prost.game.Director;
import dev.louismudrack.prost.game.PlacedBlocks;
import dev.louismudrack.prost.game.Rounds;
import dev.louismudrack.prost.hook.AfkDetector;
import dev.louismudrack.prost.hook.ConfigAreas;
import dev.louismudrack.prost.hook.DimensionDefaults;
import dev.louismudrack.prost.hook.EssentialsAfk;
import dev.louismudrack.prost.hook.Hooks;
import dev.louismudrack.prost.hook.PapiText;
import dev.louismudrack.prost.hook.PlaceholderSnapshots;
import dev.louismudrack.prost.hook.ProstExpansion;
import dev.louismudrack.prost.i18n.Lang;
import dev.louismudrack.prost.i18n.LangFiles;
import dev.louismudrack.prost.i18n.LanguageResolver;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.Theme;
import dev.louismudrack.prost.menu.MenuListener;
import dev.louismudrack.prost.meta.ProstMetrics;
import dev.louismudrack.prost.meta.UpdateChecker;
import dev.louismudrack.prost.meta.Versions;
import dev.louismudrack.prost.migration.AniDrinksMigration;
import dev.louismudrack.prost.migration.AniDrinksMigration.Outcome;
import dev.louismudrack.prost.reward.Rewards;
import dev.louismudrack.prost.roulette.Roulette;
import dev.louismudrack.prost.storage.PlayerPrefs;
import dev.louismudrack.prost.storage.SqlStatsStore;
import dev.louismudrack.prost.storage.StatsStore;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.logging.Level;

/** Prost - the drinking game. Rounds, challenges and the roulette. Not final: MockBukkit loads it as a subclass. */
public class ProstPlugin extends JavaPlugin {
    private static final String COMMAND = "prost";
    /** the API Prost is built against; api-version 1.21 alone would let 1.21.0-1.21.3 load it */
    private static final String MINIMUM_MINECRAFT = "1.21.4";

    private PluginConfig pluginConfig = PluginConfig.DEFAULTS;
    private GameConfig cfg = GameConfig.DEFAULTS;
    private List<ChallengeDef> challenges = List.of();
    private List<Rule> preparedRules = List.of();
    private DataFiles files;
    private Rounds rounds;
    private PlacedBlocks placed;
    private StatsStore stats;
    private PlayerPrefs prefs;
    private Sips sips;
    private Rewards rewards;
    private Director director;
    private Roulette roulette;
    private Hooks hooks;
    private Lang lang = Lang.empty();
    private ProstCommand command;
    private BukkitTask snapshotTask;

    @Override
    public void onEnable() {
        if (!Versions.atLeast(Bukkit.getMinecraftVersion(), MINIMUM_MINECRAFT)) {
            getLogger().severe("Prost needs Paper " + MINIMUM_MINECRAFT + " or newer - this server runs " + Bukkit.getMinecraftVersion()
                    + ", so Prost is disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        files = new DataFiles(this);
        // a config.yml that does not parse is reported once, by reloadData() below
        if (!YamlMerge.unparseable(new File(getDataFolder(), DataFiles.CONFIG))) YamlMerge.ensure(this, DataFiles.CONFIG);
        // silent here: reloadData() below parses the file again and logs its warnings once
        pluginConfig = PluginConfig.parse(files.config(problem -> { }), warning -> { });
        openStorage();
        migrateOnStart();   // before game.yml, the catalogue and rounds.yml are read, so AniDrinks' copies are what loads
        hooks = createHooks();
        getServer().getPluginManager().registerEvents(hooks, this);
        YamlMerge.ensure(this, "game.yml");
        reloadData();
        sips = new Sips(this);
        rewards = new Rewards(this);
        getServer().getPluginManager().registerEvents(rewards, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        director = new Director(this);
        getServer().getScheduler().runTaskTimer(this, () -> director.tick(), 10L, 10L);
        command = new ProstCommand(this);
        command.subcommands(subcommands());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> registerCommand(event.registrar()));
        roulette = new Roulette(this);
        getServer().getPluginManager().registerEvents(roulette, this);
        rounds = new Rounds(this);
        placed = new PlacedBlocks(this);
        getServer().getPluginManager().registerEvents(placed, this);
        rounds.load();
        getServer().getScheduler().runTaskTimer(this, () -> rounds.tickPresence(), 20L, 20L);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            PlaceholderSnapshots snapshots = new PlaceholderSnapshots(this);
            snapshotTask = getServer().getScheduler().runTaskTimer(this, () -> snapshots.publish(), 1L, 1L);
            ProstExpansion.registerFor(this, snapshots);
        }
        boolean offline = Boolean.getBoolean("prost.offline");   // the test build: no network
        if (!offline && pluginConfig.metrics()) ProstMetrics.start(this);
        if (!offline && pluginConfig.updateChecker()) {
            UpdateChecker updates = new UpdateChecker(this);
            getServer().getPluginManager().registerEvents(updates, this);
            updates.check();
        }
        // last: a plugin listening for the service may call rounds() or roundOf() right away
        getServer().getServicesManager().register(ProstApi.class, new ApiImpl(this), this, ServicePriority.Normal);
        getLogger().info("Prost enabled: " + challenges.size() + " challenges, " + preparedRules.size() + " rules, "
                + lang.languages().size() + " languages, " + rounds.all().size() + " round(s) restored");
    }

    private void registerCommand(Commands registrar) {
        List<String> aliases = pluginConfig.commandAliases();
        Set<String> registered = registrar.register(COMMAND, commandDescription(), aliases, command);
        for (String alias : notRegistered(aliases, registered))
            getLogger().warning("commands.aliases: /" + alias + " is already a command of another plugin - not registered");
    }

    /** Paper shows one description to everyone in /help: the server language's, as plain text */
    private String commandDescription() {
        return PlainTextComponentSerializer.plainText().serialize(Msg.mm(lang.template(lang.defaultLanguage(), "command.description")));
    }

    /** Paper leaves out an alias another command already has: the set holds every label it did register */
    static List<String> notRegistered(List<String> aliases, Set<String> registered) {
        return aliases.stream().filter(alias -> registered.stream().noneMatch(label -> label.equalsIgnoreCase(alias))).toList();
    }

    private void openStorage() {
        Executor sync = task -> { if (isEnabled()) getServer().getScheduler().runTask(this, task); };
        PluginConfig.Storage storage = pluginConfig.storage();
        SqlStatsStore store = storage.mysql() ? SqlStatsStore.mysql(storage, getLogger(), sync)
                : SqlStatsStore.sqlite(new File(getDataFolder(), "stats.db"), storage.tablePrefix(), getLogger(), sync);
        try {
            store.open();
        } catch (SQLException e) {
            getLogger().severe("the stats database could not be opened (" + e.getMessage() + ") - the game runs; "
                    + "stats are written once the database is reachable");
        }
        stats = store;
        prefs = new PlayerPrefs(stats);
        getServer().getPluginManager().registerEvents(prefs, this);
        getServer().getOnlinePlayers().forEach(p -> prefs.load(p));
    }

    /** at the start only into a Prost that holds no stats yet */
    Outcome migrateOnStart() { return migrate(false, () -> { }); }

    /** /prost admin migrate: also over Prost's own stats (AniDrinks' numbers replace them), loaded right away */
    public Outcome migrate() { return migrate(true, () -> loadTakenOver()); }

    private Outcome migrate(boolean overProstStats, Runnable load) {
        if (getServer().getPluginManager().getPlugin(AniDrinksMigration.SOURCE) != null) {
            getLogger().severe("AniDrinks is installed next to Prost - nothing taken over; remove AniDrinks.jar and restart");
            return Outcome.BLOCKED;
        }
        AniDrinksMigration migration = new AniDrinksMigration(getDataFolder().getParentFile(), getDataFolder(), name -> getResource(name),
                stats, getLogger());
        if (!migration.found()) return Outcome.NOTHING;
        AniDrinksMigration.Result taken;
        try {
            if (!overProstStats && migration.prostHasStats()) {
                getLogger().warning("plugins/AniDrinks not taken over: Prost already holds stats - /prost admin migrate takes it over "
                        + "anyway (AniDrinks' numbers then replace those players' Prost numbers)");
                return Outcome.BLOCKED;
            }
            taken = migration.run();
        } catch (IOException | SQLException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "taking over plugins/AniDrinks failed - the folder stays as it is; /prost admin migrate "
                    + "tries again", e);
            return Outcome.FAILED;
        }
        if (taken == null) return Outcome.NOTHING;
        load.run();
        return Outcome.DONE;
    }

    /**
     * Rounds save rounds.yml on every open, join, leave and end: with none in memory the file is either empty or a copy taken
     * over now or by a failed attempt, which the next stop would otherwise overwrite.
     */
    private void loadTakenOver() {
        reloadData();
        if (rounds.all().isEmpty()) rounds.load();
    }

    private Hooks createHooks() {
        AfkDetector detector = new AfkDetector(() -> pluginConfig.afkMinutes(), () -> System.currentTimeMillis());
        getServer().getPluginManager().registerEvents(detector, this);
        Plugin essentials = getServer().getPluginManager().getPlugin("Essentials");
        AfkProvider defaultAfk = detector;
        if (pluginConfig.useEssentials() && essentials != null && essentials.isEnabled()) {
            defaultAfk = new EssentialsAfk(essentials, detector, getLogger());
            getLogger().info("AFK: EssentialsX decides");
        }
        return new Hooks(getLogger(), defaultAfk,
                new DimensionDefaults(() -> pluginConfig, env -> getServer().getWorlds().stream().anyMatch(w -> w.getEnvironment() == env),
                        () -> Bukkit.getAllowNether(), () -> Bukkit.getAllowEnd()),
                new ConfigAreas(() -> pluginConfig));
    }

    /** each step on its own: one that throws must not cost the stats and rounds.yml after it; nothing here may schedule */
    @Override
    public void onDisable() {
        shutdownStep("the placeholder copy", () -> { if (snapshotTask != null) snapshotTask.cancel(); });
        shutdownStep("the PlaceholderAPI expansion", () -> {
            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) ProstExpansion.unregisterFor();
        });
        shutdownStep("the running challenges", () -> { if (director != null) director.shutdown(); });
        shutdownStep("the open hand-outs", () -> { if (rewards != null) rewards.flushAll(); });
        shutdownStep("the open joker prompts", () -> { if (sips != null) sips.flushAll(); });
        shutdownStep("the stats", () -> { if (stats != null) stats.close(); });
        shutdownStep("rounds.yml", () -> { if (rounds != null) rounds.save(); });
    }

    private void shutdownStep(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException | LinkageError e) {
            getLogger().log(Level.SEVERE, "stopping " + what + " failed - going on with the rest", e);
        }
    }

    public void reloadData() {
        pluginConfig = PluginConfig.parse(files.config(getLogger()::severe), getLogger()::warning);
        cfg = GameConfig.parse(files.map("game.yml"));
        challenges = Catalogue.load(files.load("challenges.yml"), getLogger());
        preparedRules = RulesFile.parse(files.load("rules.yml"));
        lang = LangFiles.load(this, pluginConfig.defaultLanguage());
        Texts.serverDefault(lang.defaultLanguage());
        Msg.bind(lang, new LanguageResolver(lang, hooks, prefs, pluginConfig.perPlayerLanguage()),
                Theme.of(pluginConfig.theme(), getLogger()::warning), placeholders());
        if (command != null) command.subcommands(subcommands());
    }

    private BiFunction<Player, String, String> placeholders() {
        boolean papi = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        return pluginConfig.parsePlaceholders() && papi ? PapiText.resolver() : (player, text) -> text;
    }

    /** on a name clash the language that comes first keeps it: the server language, then English, then the rest sorted */
    private Subcommands subcommands() {
        List<String> order = new ArrayList<>(List.of(lang.defaultLanguage(), Lang.FALLBACK));
        order.addAll(new TreeSet<>(lang.languages()));
        Map<String, Map<String, List<String>>> aliases = new LinkedHashMap<>();
        for (String code : order) if (lang.has(code)) aliases.putIfAbsent(code, lang.aliases(code));
        return Subcommands.of(aliases, getLogger()::warning);
    }

    public PluginConfig pluginConfig() { return pluginConfig; }

    public GameConfig cfg() { return cfg; }
    public List<ChallengeDef> challenges() { return challenges; }
    public List<Rule> preparedRules() { return preparedRules; }
    public DataFiles files() { return files; }
    public Rounds rounds() { return rounds; }
    public PlacedBlocks placed() { return placed; }
    public StatsStore stats() { return stats; }
    public PlayerPrefs prefs() { return prefs; }
    public Sips sips() { return sips; }
    public Rewards rewards() { return rewards; }
    public Director director() { return director; }
    public Roulette roulette() { return roulette; }
    public Hooks hooks() { return hooks; }
    public Lang lang() { return lang; }
    public ProstCommand command() { return command; }
}
