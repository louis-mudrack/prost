package dev.louismudrack.prost.game;

import dev.louismudrack.prost.api.RoundView;
import dev.louismudrack.prost.api.StatsView;
import dev.louismudrack.prost.storage.StatsRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ViewsTest {
    private static final UUID HOST = new UUID(0, 1), GUEST = new UUID(0, 2);

    @Test
    void aRoundWithoutAChallengeHasNone() {
        Round round = new Round("ABCD", HOST, Pace.PARTY, 1L);
        round.add(HOST, "Host", 10, false, 1L);
        round.add(GUEST, "Guest", 10, false, 1L);
        round.paused = true;
        RoundView view = Views.round(round, null);
        assertEquals(Optional.empty(), view.challenge());
        assertEquals("party", view.pace());
        assertEquals("ABCD", view.code());
        assertEquals(HOST, view.host());
        assertTrue(view.paused());
        assertEquals(List.of(HOST, GUEST), view.members());
    }

    @Test
    void theMemberListIsACopy() {
        Round round = new Round("ABCD", HOST, Pace.CHILL, 1L);
        round.add(HOST, "Host", 10, false, 1L);
        RoundView view = Views.round(round, null);
        round.add(GUEST, "Guest", 10, false, 1L);
        assertEquals(List.of(HOST), view.members());
    }

    @Test
    void everyPaceMapsToItsDocumentedValue() {
        for (Pace pace : Pace.values())
            assertEquals(pace.name().toLowerCase(), Views.round(new Round("ABCD", HOST, pace, 1L), null).pace());
    }

    @Test
    void statsMapEveryCounterToItsOwnComponent() {
        StatsView v = Views.stats(new StatsRow(HOST, "Host", 1, 2, 3, 4, 5, 6, 7, 8, 9));
        assertEquals(HOST, v.player());
        assertEquals("Host", v.name());
        assertEquals(1, v.sipsDrunk());
        assertEquals(2, v.sipsWater());
        assertEquals(3, v.sipsGiven());
        assertEquals(4, v.won());
        assertEquals(5, v.lost());
        assertEquals(6, v.rouletteHits());
        assertEquals(7, v.jokersUsed());
        assertEquals(8, v.rulesMade());
        assertEquals(9, v.rounds());
    }
}
