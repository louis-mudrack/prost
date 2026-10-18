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
import dev.louismudrack.prost.game.Director;
import dev.louismudrack.prost.game.PlacedBlocks;
import dev.louismudrack.prost.game.Rounds;
import dev.louismudrack.prost.menu.MenuListener;
import dev.louismudrack.prost.reward.Rewards;
import dev.louismudrack.prost.roulette.Roulette;
import dev.louismudrack.prost.storage.Stats;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Prost - the drinking game. Rounds, challenges and the roulette. */
public final class ProstPlugin extends JavaPlugin {
    private PluginConfig pluginConfig = PluginConfig.DEFAULTS;
    private GameConfig cfg = GameConfig.DEFAULTS;
    private List<ChallengeDef> challenges = List.of();
    private List<Rule> preparedRules = List.of();
    private DataFiles files;
    private Rounds rounds;
    private PlacedBlocks placed;
    private Stats stats;
    private Sips sips;
    private Rewards rewards;
    private Director director;
    private Roulette roulette;

    @Override
    public void onEnable() {
        files = new DataFiles(this);
        YamlMerge.ensure(this, "config.yml");
        YamlMerge.ensure(this, "game.yml");
        reloadData();
        stats = new Stats(this);
        try {
            stats.open();
        } catch (java.sql.SQLException e) {
            getLogger().severe("stats.db could not be opened (" + e.getMessage() + ") - the game runs, all-time stats are not kept");
        }
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
    public Stats stats() { return stats; }
    public Sips sips() { return sips; }
    public Rewards rewards() { return rewards; }
    public Director director() { return director; }
    public Roulette roulette() { return roulette; }
}
