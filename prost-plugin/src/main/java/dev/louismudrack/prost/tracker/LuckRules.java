package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.reward.Reward;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Who drinks after a luck game, from the choices and the draws. Maps come in participant order and the verdict keeps it.
 * One penalty per challenge: a winner's reward (also a forced one) only comes when nobody drinks (Resolution.Outcome#rewarded).
 */
final class LuckRules {
    /** what a chest hides */
    enum Prize { DRINK, JOKER, HANDOUT }

    /** the wheel 1-10, the die 1-6, higher or lower 1-10 */
    static final int WHEEL_MAX = 10, DIE_MAX = 6, NUMBER_MAX = 10;
    /** solo wheel: this or less drinks */
    private static final int WHEEL_SOLO_DRINKS = 3;
    /** the die: up to DIE_DRINKS drinks, from DIE_HANDS_OUT on the roller hands out, in between nothing happens */
    private static final int DIE_DRINKS = 2, DIE_HANDS_OUT = 5;

    private LuckRules() {}

    /** luck: the lowest roll drinks (a tie: all of them); solo, or the only spinner left: 1-3 drinks, 4-10 wins */
    static Verdict wheel(Kind kind, Map<UUID, Integer> rolls, int sips) {
        // the lowest of one would always drink
        if (kind == Kind.SOLO || rolls.size() == 1) return split(rolls.keySet(), id -> rolls.get(id) <= WHEEL_SOLO_DRINKS, sips);
        int lowest = Collections.min(rolls.values());
        return split(rolls.keySet(), id -> rolls.get(id) == lowest, sips);
    }

    /** a right call: the other one drinks; a wrong call: the caller */
    static Verdict coin(UUID caller, UUID other, boolean calledHeads, boolean heads, int sips) {
        boolean right = calledHeads == heads;
        return Verdict.of(Map.of(right ? other : caller, sips), List.of(right ? caller : other));
    }

    static Verdict chests(UUID player, Prize prize, int sips) {
        return switch (prize) {
            case DRINK -> Verdict.of(Map.of(player, sips), List.of());
            case JOKER -> forced(player, Reward.JOKER);
            case HANDOUT -> forced(player, Reward.HANDOUT);
        };
    }

    /** 1-2 drinks, 3-4 nothing (neither won nor lost), 5-6 hands out */
    static Verdict dice(UUID player, int roll, int sips) {
        if (roll <= DIE_DRINKS) return Verdict.of(Map.of(player, sips), List.of());
        if (roll >= DIE_HANDS_OUT) return forced(player, Reward.HANDOUT);
        return Verdict.of(Map.of(), List.of());
    }

    /** a wrong guess drinks, a right one wins */
    static Verdict highLow(Map<UUID, Boolean> guessedHigher, int shown, int next, int sips) {
        boolean higher = next > shown;
        return split(guessedHigher.keySet(), id -> guessedHigher.get(id) != higher, sips);
    }

    /** whoever took the trap drinks; nobody took it (its taker dropped out): nobody drinks */
    static Verdict boxes(Map<UUID, Integer> picks, int trap, int sips) { return split(picks.keySet(), id -> picks.get(id) == trap, sips); }

    /** 1-10, never {@code shown} */
    static int nextNumber(int shown, Random rnd) {
        int n = rnd.nextInt(NUMBER_MAX - 1) + 1;
        return n >= shown ? n + 1 : n;
    }

    private static Verdict forced(UUID player, Reward reward) { return new Verdict(Map.of(), List.of(player), Map.of(player, reward)); }

    /** who matches drinks, the others win */
    private static Verdict split(Collection<UUID> ids, Predicate<UUID> drinks, int sips) {
        Map<UUID, Integer> drink = new LinkedHashMap<>();
        List<UUID> winners = new ArrayList<>();
        for (UUID id : ids) {
            if (drinks.test(id)) drink.put(id, sips);
            else winners.add(id);
        }
        return Verdict.of(drink, winners);
    }
}
