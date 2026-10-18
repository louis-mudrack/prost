package dev.louismudrack.prost.api;

import java.util.List;
import java.util.UUID;

/**
 * A snapshot of a running challenge. kind is team, each, race, duel or solo; sips = what losers drink.
 *
 * @param id           the challenge id
 * @param kind         team, each, race, duel or solo
 * @param sips         sips the losers drink
 * @param endsAtMillis end time as epoch milliseconds
 * @param participants participant UUIDs
 */
public record ChallengeView(String id, String kind, int sips, long endsAtMillis, List<UUID> participants) {}
