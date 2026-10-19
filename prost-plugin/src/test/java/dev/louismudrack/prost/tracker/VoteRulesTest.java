package dev.louismudrack.prost.tracker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoteRulesTest {
    private static final UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3), d = new UUID(0, 4), x = new UUID(0, 99);

    @Test
    void fewestDrinkAbstainersOneMore() {
        Verdict v = VoteRules.verdict(List.of(a, b, c, d), Map.of(a, b, b, a, c, b), 3);
        assertEquals(Map.of(c, 3, d, 4), v.drinks(), "d: no votes and did not vote");
        assertEquals(List.of(b), v.winners());
    }

    @Test
    void allTiedAllDrinkNoWinner() {
        Verdict v = VoteRules.verdict(List.of(a, b, c), Map.of(a, b, b, c, c, a), 2);
        assertEquals(Map.of(a, 2, b, 2, c, 2), v.drinks());
        assertTrue(v.winners().isEmpty());
    }

    /** a vote for yourself or for somebody who is not (any more) a builder is no vote: both voters abstained */
    @Test
    void selfVotesAndVotesForOutsidersDoNotCount() {
        Verdict v = VoteRules.verdict(List.of(a, b, c), Map.of(a, a, b, x, c, a), 2);
        assertEquals(Map.of(a, 1, b, 3, c, 2), v.drinks(), "a has a vote but abstained, b has none and abstained, c has none");
        assertEquals(List.of(a), v.winners());
        assertEquals(Map.of(a, 1, b, 0, c, 0), VoteRules.counts(List.of(a, b, c), Map.of(a, a, b, x, c, a)));
    }

    @Test
    void aVoteFromAnOutsiderDoesNotCount() {
        Verdict v = VoteRules.verdict(List.of(a, b, c), Map.of(x, c, a, b, b, a, c, a), 2);
        assertEquals(Map.of(c, 2), v.drinks(), "a 2, b 1, c 0: x's vote for c is none");
        assertEquals(List.of(a), v.winners());
    }

    @Test
    void derangementNeverPicksYourself() {
        Random rnd = new Random(7);
        for (int n = 2; n <= 8; n++) {
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(new UUID(1, i));
            for (int run = 0; run < 1000; run++) {
                Map<UUID, UUID> subjects = VoteRules.derangement(ids, rnd);
                assertEquals(new HashSet<>(ids), subjects.keySet(), "everybody gets a subject");
                assertEquals(new HashSet<>(ids), new HashSet<>(subjects.values()), "everybody is somebody's subject");
                subjects.forEach((builder, subject) -> assertNotEquals(builder, subject, "nobody portrays themselves"));
            }
        }
    }

    @Test
    void derangementNeedsTwo() {
        assertThrows(IllegalArgumentException.class, () -> VoteRules.derangement(List.of(a), new Random(1)));
    }
}
