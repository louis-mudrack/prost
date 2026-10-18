package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.reward.Reward;
import java.util.List;
import java.util.Set;

/** One catalogue entry of challenges.yml (spec §6). */
public record ChallengeDef(String id, String nameDe, String nameEn, String textDe, String textEn, String tracker, String stat,
                    String source, String mode, String value, List<String> targets, int amount, int seconds, int sips,
                    int give, List<Kind> kinds, List<Reward> rewards, Set<Need> needs, List<String> alsoAvoid,
                    int minPlayers, int weight) {

    public String name(boolean de) { return de ? nameDe : nameEn; }

    public String text(boolean de) { return (de ? textDe : textEn).replace("{amount}", Integer.toString(amount)); }

    /** an "avoid" challenge is won by NOT doing something until the time is up */
    public boolean avoidType() { return "avoid".equals(tracker); }

    /** "enter the Nether/End" - skipped while a participant is already there */
    boolean entersDimension(String dimension) { return "reach".equals(tracker) && "dimension".equals(mode) && dimension.equals(value); }
}
