package dev.louismudrack.prost;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A player in a round and their pause states (spec §3.3). */
final class Member {
    final UUID id;
    String name;
    int limit;
    boolean water;
    /** /prost pause */
    boolean selfPaused;
    /** AniCore AFK */
    boolean afk;
    /** disconnected or /unplug (the bot never plays) */
    boolean away;
    /** creative or spectator */
    boolean wrongMode;
    long awaySince, joinedAt;
    int jokers, solos, duels;
    final List<SipLedger.Sip> history = new ArrayList<>();
    final List<Long> rouletteHits = new ArrayList<>();
    final Tally tally = new Tally();

    Member(UUID id, String name, int limit, boolean water, long joinedAt) {
        this.id = id;
        this.name = name;
        this.limit = limit;
        this.water = water;
        this.joinedAt = joinedAt;
    }

    boolean active(boolean roundPaused) { return !roundPaused && !selfPaused && !afk && !away && !wrongMode; }
}
