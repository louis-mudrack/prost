package dev.louismudrack.prost.game;

import dev.louismudrack.prost.drink.SipLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A player in a round and their pause states (spec §3.3). */
public final class Member {
    public final UUID id;
    public String name;
    public int limit;
    public boolean water;
    /** /prost pause */
    public boolean selfPaused;
    /** AniCore AFK */
    public boolean afk;
    /** disconnected or /unplug (the bot never plays) */
    public boolean away;
    /** creative or spectator, or outside config.yml worlds */
    public boolean wrongMode;
    long awaySince, joinedAt;
    public int jokers, solos, duels;
    public final List<SipLedger.Sip> history = new ArrayList<>();
    public final List<Long> rouletteHits = new ArrayList<>();
    public final Tally tally = new Tally();

    Member(UUID id, String name, int limit, boolean water, long joinedAt) {
        this.id = id;
        this.name = name;
        this.limit = limit;
        this.water = water;
        this.joinedAt = joinedAt;
    }

    boolean active(boolean roundPaused) { return !roundPaused && !selfPaused && !afk && !away && !wrongMode; }
}
