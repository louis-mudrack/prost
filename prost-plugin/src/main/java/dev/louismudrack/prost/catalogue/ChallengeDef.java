package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.reward.Reward;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One catalogue entry of challenges.yml. */
public record ChallengeDef(String id, Map<String, String> names, Map<String, String> texts, String tracker, String stat,
                    String source, String mode, String value, List<String> targets, int amount, int seconds, int sips,
                    int give, List<Kind> kinds, List<Reward> rewards, Set<Need> needs, List<String> alsoAvoid,
                    int minPlayers, int weight) {

    public String name(String lang) { return Texts.pick(names, lang); }

    public String text(String lang) { return Texts.pick(texts, lang).replace("{amount}", Integer.toString(amount)); }

    /** an "avoid" challenge is won by NOT doing something until the time is up */
    public boolean avoidType() { return "avoid".equals(tracker); }

    /** "enter the Nether/End" - skipped while a participant is already there */
    boolean entersDimension(String dimension) { return "reach".equals(tracker) && "dimension".equals(mode) && dimension.equals(value); }
}
