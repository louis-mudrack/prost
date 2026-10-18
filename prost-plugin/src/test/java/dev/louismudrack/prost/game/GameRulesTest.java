package dev.louismudrack.prost.game;

import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.drink.SipLedger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static dev.louismudrack.prost.Checks.*;

class GameRulesTest {
    static Resolution.Progress pr(UUID id, int value, boolean done, long doneAt) { return new Resolution.Progress(id, value, done, false, doneAt); }

    @Test
    void roundsFromAniDrinksKeepTheirRules() {
        Map<String, Object> old = new java.util.HashMap<>(Map.of("code", "L9D5", "host", A.toString(), "pace", "party", "created", 1L,
                "rules", java.util.List.of(Map.of("de", "Niemand darf \"ich\" sagen.", "en", "Nobody may say \"I\"."))));
        Round r = RoundCodec.decode(old, 20);
        eq("German text", "Niemand darf \"ich\" sagen.", r.rules.get(0).text("de"));
        eq("English text", "Nobody may say \"I\".", r.rules.get(0).text("en"));
        r.rules.add(dev.louismudrack.prost.catalogue.Rule.custom("Diggi"));
        Round again = RoundCodec.decode(RoundCodec.encode(r), 20);
        eq("a typed rule survives a restart", "Diggi", again.rules.get(1).text("en"));
    }

    @Test
    void resolution() {
        Resolution.Outcome solo = Resolution.resolve(Kind.SOLO, false, 3, 0, List.of(pr(A, 16, true, 10)));
        eq("solo done: winner", List.of(A), solo.winners());
        eq("solo done: nobody drinks", Map.of(), solo.drinks());
        eq("solo missed: drinks the sips", Map.of(A, 3), Resolution.resolve(Kind.SOLO, false, 3, 0, List.of(pr(A, 5, false, 0))).drinks());

        Resolution.Outcome each = Resolution.resolve(Kind.EACH, false, 2, 0, List.of(pr(A, 25, true, 5), pr(B, 3, false, 0), pr(C, 25, true, 9)));
        eq("each: everyone who made it wins", List.of(A, C), each.winners());
        eq("each: only the one who missed drinks", Map.of(B, 2), each.drinks());

        Resolution.Outcome team = Resolution.resolve(Kind.TEAM, false, 3, 60, List.of(pr(A, 10, false, 0), pr(B, 40, false, 0), pr(C, 10, false, 0)));
        check("team: 60 together reached", team.success());
        eq("team: the biggest contributor picks", B, team.mvp());
        eq("team reached: nobody drinks", Map.of(), team.drinks());
        Resolution.Outcome teamFail = Resolution.resolve(Kind.TEAM, false, 3, 61, List.of(pr(A, 10, false, 0), pr(B, 40, false, 0), pr(C, 10, false, 0)));
        check("team missed", !teamFail.success());
        eq("team missed: everyone drinks", Map.of(A, 3, B, 3, C, 3), teamFail.drinks());
        Resolution.Outcome avoidTeam = Resolution.resolve(Kind.TEAM, true, 2, 0,
                List.of(new Resolution.Progress(A, 1, true, false, 0), new Resolution.Progress(B, 0, false, true, 0)));
        check("team avoid: one break fails everyone", !avoidTeam.success());
        eq("team avoid: everyone drinks", Map.of(A, 2, B, 2), avoidTeam.drinks());

        Resolution.Outcome race = Resolution.resolve(Kind.RACE, false, 2, 0, List.of(pr(A, 7, false, 0), pr(B, 10, true, 50), pr(C, 3, false, 0)));
        eq("race: the finisher wins", List.of(B), race.winners());
        eq("race: the least progress drinks", Map.of(C, 2), race.drinks());
        eq("race: a tie at the bottom drinks together", Map.of(A, 2, C, 2),
                Resolution.resolve(Kind.RACE, false, 2, 0, List.of(pr(A, 3, false, 0), pr(B, 10, true, 50), pr(C, 3, false, 0))).drinks());
        Resolution.Outcome raceNone = Resolution.resolve(Kind.RACE, false, 2, 0, List.of(pr(A, 4, false, 0), pr(B, 6, false, 0)));
        eq("race nobody finished: no winner", List.of(), raceNone.winners());
        eq("race nobody finished: the least still drinks", Map.of(A, 2), raceNone.drinks());
        eq("race: two finishers in one tick - the earlier entry wins", List.of(A),
                Resolution.resolve(Kind.RACE, false, 2, 0, List.of(pr(A, 10, true, 50), pr(B, 10, true, 50), pr(C, 1, false, 0))).winners());

        Resolution.Outcome duel = Resolution.resolve(Kind.DUEL, false, 2, 0, List.of(pr(A, 5, false, 0), pr(B, 8, false, 0)));
        eq("duel at time: more progress wins", List.of(B), duel.winners());
        eq("duel: the loser drinks", Map.of(A, 2), duel.drinks());
        eq("duel: first to finish wins even with less counted", List.of(A),
                Resolution.resolve(Kind.DUEL, false, 2, 0, List.of(pr(A, 10, true, 40), pr(B, 12, true, 60))).winners());
        Resolution.Outcome tie = Resolution.resolve(Kind.DUEL, false, 2, 0, List.of(pr(A, 5, false, 0), pr(B, 5, false, 0)));
        eq("duel tie: no winner", List.of(), tie.winners());
        eq("duel tie: both drink", Map.of(A, 2, B, 2), tie.drinks());

        eq("nobody took part: nothing happens", Map.of(), Resolution.resolve(Kind.EACH, false, 2, 0, List.of()).drinks());

        // one penalty per challenge: when anyone drinks, the winners get no reward on top
        eq("solo won: rewarded", List.of(A), solo.rewarded());
        eq("each with a miss: the winners get nothing on top", List.of(), each.rewarded());
        eq("each, everyone made it: everyone rewarded", List.of(A, C),
                Resolution.resolve(Kind.EACH, false, 2, 0, List.of(pr(A, 25, true, 5), pr(C, 25, true, 9))).rewarded());
        eq("team reached: rewarded", List.of(A, B, C), team.rewarded());
        eq("team missed: nobody rewarded", List.of(), teamFail.rewarded());
        eq("race: the slowest drinks, so no reward", List.of(), race.rewarded());
        eq("duel: the loser drinks, so no reward", List.of(), duel.rewarded());
        eq("duel tie: nobody rewarded", List.of(), tie.rewarded());
    }

    @Test
    void rounds() {
        Round r = new Round("KX4P", A, Pace.NORMAL, 1_000);
        r.add(A, "Kasheyy", 20, false, 1_000);
        r.add(B, "Ice", 15, true, 2_000);
        check("not full at 2 of 3", !r.full(3));
        r.add(C, "Erd", 20, false, 3_000);
        check("full at 3 of 3", r.full(3));
        eq("everyone active", List.of(A, B, C), r.activeIds());
        r.member(B).selfPaused = true;
        r.member(C).afk = true;
        eq("paused and AFK members are not active", List.of(A), r.activeIds());
        r.paused = true;
        eq("a paused round has nobody active", List.of(), r.activeIds());
        r.paused = false;
        eq("the host leaving hands over to the earliest member", B, r.remove(A));
        eq("the new host", B, r.host);
        eq("a normal member leaving changes no host", null, r.remove(C));
        eq("the last member leaving leaves no host", null, r.remove(B));
        check("an empty round", r.members.isEmpty());

        Round rr = new Round("ZZ22", A, Pace.PARTY, 5);
        for (int i = 0; i < 6; i++) rr.addRule(Rule.custom("R" + i), 5);
        eq("rules stay at the maximum", 5, rr.rules.size());
        eq("the oldest rule made room", "R1", rr.rules.get(0).text("de"));

        String code = Round.newCode(new Random(5), Set.of());
        check("codes are 4 characters without look-alikes", code.length() == 4 && code.chars().noneMatch(ch -> "0O1I".indexOf(ch) >= 0));

        Round s = new Round("AB34", A, Pace.CHILL, 7);
        Member m = s.add(A, "Kasheyy", 25, true, 7);
        m.jokers = 2; m.solos = 1; m.history.add(new SipLedger.Sip(100, 3)); m.rouletteHits.add(200L); m.tally.drunk = 9;
        s.rules.add(new Rule(Map.of("de", "Linke Hand", "en", "Left hand")));
        s.used.add("mine_iron");
        s.challenges = 4;
        Round back = RoundCodec.decode(RoundCodec.encode(s), 20);
        eq("code survives", "AB34", back.code);
        eq("pace survives", Pace.CHILL, back.pace);
        eq("limit survives", 25, back.member(A).limit);
        check("water survives", back.member(A).water);
        eq("jokers survive", 2, back.member(A).jokers);
        eq("the last hour's sips survive (the limit keeps working)", 3, SipLedger.recent(back.member(A).history, 150));
        eq("roulette hits survive", List.of(200L), back.member(A).rouletteHits);
        eq("tally survives", 9, back.member(A).tally.drunk);
        eq("rules survive", "Left hand", back.rules.get(0).text("en"));
        check("used challenges survive", back.used.contains("mine_iron"));
        check("after a restart everyone is away until they rejoin", back.member(A).away);
        Round partial = RoundCodec.decode(Map.of("code", "QQ77", "host", A.toString(), "members", Map.of(A.toString(), Map.of("name", "X"))), 20);
        eq("a member with missing fields gets the default limit", 20, partial.member(A).limit);
        eq("missing pace = normal", Pace.NORMAL, partial.pace);
        eq("no host = unusable", null, RoundCodec.decode(Map.of("code", "QQ77"), 20));
    }

    @Test
    void joinRules() {
        Round r = new Round("AA22", A, Pace.NORMAL, 0);
        r.add(A, "a", 20, false, 0);
        eq("join an open round", JoinRules.Result.OK, JoinRules.check(false, r, 8));
        eq("one round at a time", JoinRules.Result.ALREADY_IN_ROUND, JoinRules.check(true, r, 8));
        eq("unknown code", JoinRules.Result.NO_SUCH_ROUND, JoinRules.check(false, null, 8));
        eq("full round", JoinRules.Result.FULL, JoinRules.check(false, r, 1));
        check("away 10 minutes with a 10-minute grace: removed", JoinRules.graceOver(true, 0, 600_000, 600_000));
        check("away 9 minutes: still in", !JoinRules.graceOver(true, 0, 540_000, 600_000));
        check("here: never removed", !JoinRules.graceOver(false, 0, 9_999_999, 600_000));
    }

    @Test
    void summaryRules() {
        Round r = new Round("SS11", A, Pace.NORMAL, 0);
        Member a = r.add(A, "a", 20, false, 1), b = r.add(B, "b", 20, false, 2), c = r.add(C, "c", 20, false, 3);
        a.tally.won = 2; b.tally.won = 3; c.tally.won = 3;
        a.tally.drunk = 9;
        eq("most wins, tie = who joined first", B, SummaryRules.top(r.members.values(), m -> m.tally.won));
        eq("most sips", A, SummaryRules.top(r.members.values(), m -> m.tally.drunk + m.tally.water));
        eq("nobody handed out = no title", null, SummaryRules.top(r.members.values(), m -> m.tally.given));
    }
}
