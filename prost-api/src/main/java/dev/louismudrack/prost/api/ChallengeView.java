package dev.louismudrack.prost.api;

import java.util.List;
import java.util.UUID;

/**
 * A snapshot of a running challenge. kind is team, each, race, duel, solo, reaction, luck or vote; sips = what losers drink.
 * A hidden-clock game (hot potato) reports its real end in endsAtMillis although players see "??".
 *
 * @param id           the challenge id
 * @param kind         team, each, race, duel, solo, reaction, luck or vote
 * @param sips         sips the losers drink
 * @param endsAtMillis end time as epoch milliseconds
 * @param participants participant UUIDs
 */
public record ChallengeView(String id, String kind, int sips, long endsAtMillis, List<UUID> participants) {
    /** copies the participant list so a view is an immutable snapshot */
    public ChallengeView {
        participants = List.copyOf(participants);
    }
}
