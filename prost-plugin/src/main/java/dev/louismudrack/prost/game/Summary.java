package dev.louismudrack.prost.game;

import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.roulette.Roulette;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** End-of-round numbers (spec §10.4): for a member who leaves, and for everyone when the round ends. */
final class Summary {
    private Summary() {}

    static void member(Player to, Round r, Member m) {
        UUID id = to.getUniqueId();
        Msg.tell(to, "summary.yours");
        Msg.send(to, line(id, m));
    }

    static void round(Round r) {
        UUID mvp = SummaryRules.top(r.members.values(), m -> m.tally.won);
        UUID drinker = SummaryRules.top(r.members.values(), m -> m.tally.drunk + m.tally.water);
        UUID giver = SummaryRules.top(r.members.values(), m -> m.tally.given);
        long minutes = (System.currentTimeMillis() - r.createdAt) / 60_000L;
        for (UUID id : r.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            Msg.tell(p, "summary.over", V.of("minutes", minutes), V.of("challenges", r.challenges));
            for (Member m : r.members.values()) Msg.tell(p, "summary.member_line", V.of("name", m.name), V.mm("numbers", line(id, m)));
            if (mvp != null) Msg.tell(p, "summary.mvp", V.of("name", r.member(mvp).name));
            if (drinker != null) Msg.tell(p, "summary.drinker", V.of("name", r.member(drinker).name));
            if (giver != null) Msg.tell(p, "summary.generous", V.of("name", r.member(giver).name));
        }
    }

    private static String line(UUID viewer, Member m) {
        Tally t = m.tally;
        String water = t.water > 0 ? " " + Msg.str(viewer, "summary.water", V.of("water", t.water)) : "";
        return Msg.str(viewer, "summary.numbers", V.of("drunk", t.drunk), V.mm("water", water), V.of("given", t.given), V.of("won", t.won),
                V.of("lost", t.lost), V.of("roulette", t.roulette), V.of("jokers", t.jokersUsed), V.of("rules", t.rulesMade));
    }
}
