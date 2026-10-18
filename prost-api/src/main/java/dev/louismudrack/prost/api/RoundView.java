package dev.louismudrack.prost.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A snapshot of a running round. pace is chill, normal or party; members in join order.
 *
 * @param code      the round's join code
 * @param host      the host's UUID
 * @param pace      chill, normal or party
 * @param paused    whether the round is paused
 * @param members   member UUIDs in join order
 * @param challenge the running challenge, if any
 */
public record RoundView(String code, UUID host, String pace, boolean paused, List<UUID> members, Optional<ChallengeView> challenge) {
    /** copies the member list so a view is an immutable snapshot; challenge must not be null (use Optional.empty()) */
    public RoundView {
        members = List.copyOf(members);
        Objects.requireNonNull(challenge, "challenge");
    }
}
