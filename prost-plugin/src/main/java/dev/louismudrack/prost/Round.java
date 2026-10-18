package dev.louismudrack.prost;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** One running game (spec §3): host, members in join order, pace, rules, what was played. */
final class Round {
    /** "pick the next one": the winner's choice for the next draw */
    record Queued(String id, Kind kind, List<UUID> players) {}

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    final String code;
    UUID host;
    Pace pace;
    final long createdAt;
    boolean paused;
    long nextDrawAt;
    int challenges;
    long lastRuleReminder;
    final LinkedHashMap<UUID, Member> members = new LinkedHashMap<>();
    final List<Rule> rules = new ArrayList<>();
    final Set<String> used = new HashSet<>();
    Queued queued;

    Round(String code, UUID host, Pace pace, long createdAt) {
        this.code = code;
        this.host = host;
        this.pace = pace;
        this.createdAt = createdAt;
    }

    static String newCode(Random rnd, Set<String> taken) {
        while (true) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < CODE_LENGTH; i++) b.append(CODE_CHARS.charAt(rnd.nextInt(CODE_CHARS.length())));
            if (!taken.contains(b.toString())) return b.toString();
        }
    }

    boolean full(int max) { return members.size() >= max; }

    Member add(UUID id, String name, int limit, boolean water, long now) {
        Member m = new Member(id, name, limit, water, now);
        members.put(id, m);
        return m;
    }

    /** removes a member; returns the new host when the host left and someone remains, else null */
    UUID remove(UUID id) {
        members.remove(id);
        if (!id.equals(host) || members.isEmpty()) return null;
        host = members.keySet().iterator().next();
        return host;
    }

    Member member(UUID id) { return members.get(id); }

    List<UUID> activeIds() {
        List<UUID> out = new ArrayList<>();
        for (Member m : members.values()) if (m.active(paused)) out.add(m.id);
        return out;
    }

    boolean isActive(UUID id) {
        Member m = members.get(id);
        return m != null && m.active(paused);
    }

    /** a new rule; when the list is full the oldest one makes room (the rule menu lets the winner pick which instead) */
    void addRule(Rule rule, int max) {
        rules.add(rule);
        while (rules.size() > Math.max(1, max)) rules.remove(0);
    }
}
