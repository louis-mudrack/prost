package dev.louismudrack.prost.storage;

/** The all-time counters per player and their columns. */
public enum StatsField {
    DRUNK("sips_drunk"), WATER("sips_water"), GIVEN("sips_given"), WON("won"), LOST("lost"), ROULETTE("roulette_hits"),
    JOKERS("jokers_used"), RULES("rules_made"), ROUNDS("rounds");

    public final String column;

    StatsField(String column) { this.column = column; }
}
