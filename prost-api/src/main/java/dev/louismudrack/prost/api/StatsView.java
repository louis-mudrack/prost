package dev.louismudrack.prost.api;

import java.util.UUID;

/**
 * A player's all-time numbers.
 *
 * @param player       the player's UUID
 * @param name         the last known player name
 * @param sipsDrunk    sips drunk
 * @param sipsWater    sips drunk as water
 * @param sipsGiven    sips handed out
 * @param won          challenges won
 * @param lost         challenges lost
 * @param rouletteHits times the roulette hit the player
 * @param jokersUsed   jokers used
 * @param rulesMade    rules made
 * @param rounds       rounds played
 */
public record StatsView(UUID player, String name, int sipsDrunk, int sipsWater, int sipsGiven, int won, int lost, int rouletteHits,
                        int jokersUsed, int rulesMade, int rounds) {}
