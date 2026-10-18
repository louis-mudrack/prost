package dev.louismudrack.prost.storage;

import java.util.UUID;

/** One player's all-time numbers. */
public record StatsRow(UUID id, String name, int drunk, int water, int given, int won, int lost, int roulette, int jokers,
                       int rules, int rounds) {}
