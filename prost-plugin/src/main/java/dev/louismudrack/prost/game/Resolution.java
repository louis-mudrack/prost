package dev.louismudrack.prost.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Progress at the end of a challenge -> who drinks how much and who wins. Callers shuffle the list first,
 * so a tie goes to whoever came first in the shuffled order (= random).
 */
final class Resolution {
    /** value = progress in the challenge's unit; done = goal reached (avoid: not broken); doneAt = millis, 0 = not done */
    record Progress(UUID id, int value, boolean done, boolean broken, long doneAt) {}

    /** drinks in participant order; mvp chooses a Team reward; success = someone/the team made it */
    record Outcome(Map<UUID, Integer> drinks, List<UUID> winners, UUID mvp, boolean success) {
        /** one penalty per challenge (owner, 2026-10-17): when anyone drinks, the winners get no reward on top */
        List<UUID> rewarded() { return drinks.isEmpty() ? winners : List.of(); }
    }

    private Resolution() {}

    static Outcome resolve(Kind kind, boolean avoid, int sips, int teamTarget, List<Progress> ps) {
        if (ps.isEmpty()) return new Outcome(Map.of(), List.of(), null, false);
        return switch (kind) {
            case SOLO, EACH -> each(sips, ps);
            case TEAM -> team(avoid, sips, teamTarget, ps);
            case RACE -> race(sips, ps);
            case DUEL -> duel(sips, ps);
            case REACTION -> throw new IllegalStateException("decided by its game");
        };
    }

    private static Outcome each(int sips, List<Progress> ps) {
        Map<UUID, Integer> drinks = new LinkedHashMap<>();
        List<UUID> winners = new ArrayList<>();
        for (Progress p : ps) {
            if (p.done()) winners.add(p.id());
            else drinks.put(p.id(), sips);
        }
        return new Outcome(drinks, winners, null, !winners.isEmpty());
    }

    private static Outcome team(boolean avoid, int sips, int target, List<Progress> ps) {
        boolean success = avoid ? ps.stream().noneMatch(p -> p.broken())
                : ps.stream().mapToInt(p -> p.value()).sum() >= target;
        if (!success) {
            Map<UUID, Integer> drinks = new LinkedHashMap<>();
            for (Progress p : ps) drinks.put(p.id(), sips);
            return new Outcome(drinks, List.of(), null, false);
        }
        Progress mvp = ps.get(0);
        for (Progress p : ps) if (p.value() > mvp.value()) mvp = p;
        return new Outcome(Map.of(), ps.stream().map(p -> p.id()).toList(), mvp.id(), true);
    }

    private static Outcome race(int sips, List<Progress> ps) {
        Progress winner = null;
        for (Progress p : ps) if (p.done() && (winner == null || p.doneAt() < winner.doneAt())) winner = p;
        int least = Integer.MAX_VALUE;
        for (Progress p : ps) if (p != winner) least = Math.min(least, p.value());
        Map<UUID, Integer> drinks = new LinkedHashMap<>();
        for (Progress p : ps) if (p != winner && p.value() == least) drinks.put(p.id(), sips);
        return new Outcome(drinks, winner == null ? List.of() : List.of(winner.id()), null, winner != null);
    }

    private static Outcome duel(int sips, List<Progress> ps) {
        Progress a = ps.get(0), b = ps.size() > 1 ? ps.get(1) : null;
        if (b == null) return each(sips, ps);
        Progress winner;
        if (a.done() && (!b.done() || a.doneAt() <= b.doneAt())) winner = a;
        else if (b.done()) winner = b;
        else if (a.value() != b.value()) winner = a.value() > b.value() ? a : b;
        else return new Outcome(new LinkedHashMap<>(Map.of(a.id(), sips, b.id(), sips)), List.of(), null, false);
        Progress loser = winner == a ? b : a;
        return new Outcome(Map.of(loser.id(), sips), List.of(winner.id()), null, true);
    }
}
