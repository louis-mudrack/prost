package dev.louismudrack.prost.reward;

import java.util.List;

/** Reward decisions without Bukkit (spec §4.3, §4.5). */
public final class RewardRules {
    private static final String COLOUR_CODES = "[§&][0-9a-fk-orA-FK-OR]";

    private RewardRules() {}

    /** a Team gets rule/joker/next; an entry that lists none of them gives every participant a joker */
    public static List<Reward> teamOptions(List<Reward> listed) {
        List<Reward> group = listed.stream().filter(r -> r != Reward.HANDOUT).toList();
        return group.isEmpty() ? List.of(Reward.JOKER) : group;
    }

    static boolean jokerFits(int held, int max) { return held < max; }

    /** with nobody else in the round the sips go to people at the real-life table */
    static boolean realLifeHandout(int members) { return members <= 1; }

    /** a typed rule: no colour codes, trimmed, cut to the maximum; null when empty or "abbrechen"/"cancel" */
    static String cleanRule(String typed, int max) {
        String s = typed.replaceAll(COLOUR_CODES, "").trim();
        if (s.isEmpty() || s.equalsIgnoreCase("abbrechen") || s.equalsIgnoreCase("cancel")) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
