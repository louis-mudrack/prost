package dev.louismudrack.prost.reward;

import dev.louismudrack.prost.game.Pace;
import dev.louismudrack.prost.game.Round;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.louismudrack.prost.Checks.*;

class RewardRulesTest {
    @Test
    void rewardRules() {
        eq("a team never hands out", List.of(Reward.RULE, Reward.NEXT), RewardRules.teamOptions(List.of(Reward.HANDOUT, Reward.RULE, Reward.NEXT)));
        eq("an entry with only hand-out gives a team a joker each", List.of(Reward.JOKER), RewardRules.teamOptions(List.of(Reward.HANDOUT)));
        check("a joker fits below the maximum", RewardRules.jokerFits(1, 2));
        check("no third joker", !RewardRules.jokerFits(2, 2));
        check("a solo round hands out in real life", RewardRules.realLifeHandout(1));
        check("a group hands out in the game", !RewardRules.realLifeHandout(3));
        List<String> cancel = List.of("abbrechen", "cancel");
        eq("a typed rule is trimmed", "Nur flüstern", RewardRules.cleanRule("  Nur flüstern ", 60, cancel));
        eq("colour codes are stripped", "Rot", RewardRules.cleanRule("§cRot", 60, cancel));
        eq("too long is cut", "abcde", RewardRules.cleanRule("abcdefgh", 5, cancel));
        eq("'abbrechen' cancels", null, RewardRules.cleanRule("abbrechen", 60, cancel));
        eq("'cancel' cancels", null, RewardRules.cleanRule("Cancel", 60, cancel));
        eq("only blanks cancel", null, RewardRules.cleanRule("   ", 60, cancel));
        eq("a translator's cancel word cancels", null, RewardRules.cleanRule("Annuler", 60, List.of("annuler")));
        eq("a word that is not a cancel word in this language is a rule", "abbrechen", RewardRules.cleanRule("abbrechen", 60, List.of("annuler")));
    }

    /** an own-rule window that ran out must not swallow the player's next chat message */
    @Test
    void ruleInputs() {
        RuleInputs in = new RuleInputs();
        Round r = new Round("RI11", A, Pace.NORMAL, 0);
        in.open(A, r, 1_000);
        eq("a rule typed in time is taken", r, in.take(A, 900).round());
        eq("an input is taken once", null, in.take(A, 900));
        in.open(B, r, 1_000);
        eq("typed after the time ran out: not taken, the message stays chat", null, in.take(B, 1_001));
        eq("and the stale input is gone", null, in.take(B, 500));
        eq("no input: nothing taken", null, in.take(C, 0));
    }
}
