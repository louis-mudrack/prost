package dev.louismudrack.prost;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/** Prost - the drinking game. Game rules: docs of the AniDrinks spec (rounds, challenges, roulette). */
public final class ProstPlugin extends JavaPlugin {
    private DrinksConfig cfg = DrinksConfig.DEFAULTS;
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
        DrinksCommand command = new DrinksCommand(this);
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

    void reloadData() {
        cfg = DrinksConfig.parse(files.map("drinks.yml"));
        challenges = Catalogue.load(files.load("challenges.yml"), getLogger());
        preparedRules = RulesFile.parse(files.load("rules.yml"));
    }

    DrinksConfig cfg() { return cfg; }
    List<ChallengeDef> challenges() { return challenges; }
    List<Rule> preparedRules() { return preparedRules; }
    DataFiles files() { return files; }
    Rounds rounds() { return rounds; }
    PlacedBlocks placed() { return placed; }
    Stats stats() { return stats; }
    Sips sips() { return sips; }
    Rewards rewards() { return rewards; }
    Director director() { return director; }
    Roulette roulette() { return roulette; }
}
