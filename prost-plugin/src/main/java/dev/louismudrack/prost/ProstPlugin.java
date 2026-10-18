package dev.louismudrack.prost;

import dev.louismudrack.prost.catalogue.Catalogue;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.catalogue.RulesFile;
import dev.louismudrack.prost.command.ProstCommand;
import dev.louismudrack.prost.config.DataFiles;
import dev.louismudrack.prost.config.GameConfig;
import dev.louismudrack.prost.config.PluginConfig;
import dev.louismudrack.prost.config.YamlMerge;
import dev.louismudrack.prost.drink.Sips;
import dev.louismudrack.prost.api.ProstApi;
import dev.louismudrack.prost.game.Director;
import dev.louismudrack.prost.game.PlacedBlocks;
import dev.louismudrack.prost.game.Rounds;
import dev.louismudrack.prost.hook.ConfigAreas;
import dev.louismudrack.prost.hook.DimensionDefaults;
import dev.louismudrack.prost.hook.Hooks;
import dev.louismudrack.prost.menu.MenuListener;
import dev.louismudrack.prost.reward.Rewards;
import dev.louismudrack.prost.roulette.Roulette;
import dev.louismudrack.prost.storage.PlayerPrefs;
import dev.louismudrack.prost.storage.SqlStatsStore;
import dev.louismudrack.prost.storage.StatsStore;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executor;

/** Prost - the drinking game. Rounds, challenges and the roulette. */
public final class ProstPlugin extends JavaPlugin {
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

    @Override
    public void onEnable() {
        files = new DataFiles(this);
        YamlMerge.ensure(this, "config.yml");
        YamlMerge.ensure(this, "game.yml");
        reloadData();
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
        hooks = new Hooks(getLogger(), p -> false,   // AFK detection: Task 7
                new DimensionDefaults(() -> pluginConfig, env -> getServer().getWorlds().stream().anyMatch(w -> w.getEnvironment() == env),
                        () -> Bukkit.getAllowNether(), () -> Bukkit.getAllowEnd()),
                new ConfigAreas(() -> pluginConfig));
        getServer().getPluginManager().registerEvents(hooks, this);
        getServer().getServicesManager().register(ProstApi.class, new ApiImpl(this), this, ServicePriority.Normal);
        prefs = new PlayerPrefs(stats);
        getServer().getPluginManager().registerEvents(prefs, this);
        getServer().getOnlinePlayers().forEach(p -> prefs.load(p));
        sips = new Sips(this);
        rewards = new Rewards(this);
        getServer().getPluginManager().registerEvents(rewards, this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        director = new Director(this);
        getServer().getScheduler().runTaskTimer(this, () -> director.tick(), 10L, 10L);
        ProstCommand command = new ProstCommand(this);
        getCommand("prost").setExecutor(command);
        getCommand("prost").setTabCompleter(command);
        roulette = new Roulette(this);
        getServer().getPluginManager().registerEvents(roulette, this);
        rounds = new Rounds(this);
        placed = new PlacedBlocks(this);
        getServer().getPluginManager().registerEvents(placed, this);
        rounds.load();
        getServer().getScheduler().runTaskTimer(this, () -> rounds.tickPresence(), 20L, 20L);
        getLogger().info("Prost enabled: " + challenges.size() + " challenges, " + preparedRules.size() + " rules, "
                + rounds.all().size() + " round(s) restored");
    }

    @Override
    public void onDisable() {
        if (director != null) director.shutdown();
        if (rewards != null) rewards.flushAll();
        if (sips != null) sips.flushAll();
        if (stats != null) stats.close();
        if (rounds != null) rounds.save();
    }

    public void reloadData() {
        pluginConfig = PluginConfig.parse(files.map("config.yml"), getLogger()::warning);
        cfg = GameConfig.parse(files.map("game.yml"));
        challenges = Catalogue.load(files.load("challenges.yml"), getLogger());
        preparedRules = RulesFile.parse(files.load("rules.yml"));
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
}
