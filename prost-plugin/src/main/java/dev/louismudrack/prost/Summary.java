package dev.louismudrack.prost;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** End-of-round numbers (spec §10.4): for a member who leaves, and for everyone when the round ends. */
final class Summary {
    private Summary() {}

    static void member(Player to, Round r, Member m) {
        boolean de = Msg.de(to.getUniqueId());
        to.sendMessage(Msg.mm(Msg.PREFIX + "<gradient:#ffd94d:#ff8f3f>" + (de ? "Deine Runde" : "Your round") + "</gradient>"));
        to.sendMessage(Msg.mm(Msg.PREFIX + line(m, de)));
    }

    static void round(Round r) {
        UUID mvp = SummaryRules.top(r.members.values(), m -> m.tally.won);
        UUID drinker = SummaryRules.top(r.members.values(), m -> m.tally.drunk + m.tally.water);
        UUID giver = SummaryRules.top(r.members.values(), m -> m.tally.given);
        long minutes = (System.currentTimeMillis() - r.createdAt) / 60_000L;
        for (UUID id : r.members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            boolean de = Msg.de(id);
            p.sendMessage(Msg.mm(Msg.PREFIX + "<gradient:#ffd94d:#ff8f3f>" + (de ? "Die Runde ist vorbei - Prost!" : "The round is over - cheers!")
                    + "</gradient> <dark_gray>(" + minutes + " min, " + r.challenges + (de ? " Herausforderungen)" : " challenges)")));
            for (Member m : r.members.values()) p.sendMessage(Msg.mm(Msg.PREFIX + Msg.HL + Msg.esc(m.name) + " <dark_gray>» " + line(m, de)));
            title(p, r, mvp, "MVP", "MVP");
            title(p, r, drinker, "Schluckspecht", "Biggest drinker");
            title(p, r, giver, "Gönner", "Most generous");
        }
    }

    private static void title(Player to, Round r, UUID who, String de, String en) {
        if (who == null) return;
        Member m = r.member(who);
        to.sendMessage(Msg.mm(Msg.PREFIX + "<gold>" + Msg.t(to.getUniqueId(), de, en) + ": " + Msg.HL + Msg.esc(m.name)));
    }

    private static String line(Member m, boolean de) {
        Tally t = m.tally;
        return "<gray>" + (de ? "getrunken " : "drunk ") + Msg.HL + t.drunk + (t.water > 0 ? " <gray>+ " + Msg.HL + t.water + (de ? " <gray>Wasser" : " <gray>water") : "")
                + " <dark_gray>· <gray>" + (de ? "verteilt " : "handed out ") + Msg.HL + t.given
                + " <dark_gray>· <gray>" + (de ? "Siege " : "won ") + Msg.HL + t.won + " <dark_gray>/ <gray>" + (de ? "Niederlagen " : "lost ") + Msg.HL + t.lost
                + " <dark_gray>· <gray>Roulette " + Msg.HL + t.roulette + " <dark_gray>· <gray>Joker " + Msg.HL + t.jokersUsed
                + " <dark_gray>· <gray>" + (de ? "Regeln " : "rules ") + Msg.HL + t.rulesMade;
    }
}
