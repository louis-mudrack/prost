package dev.louismudrack.prost.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ViewsTest {
    @Test
    void roundViewIsASnapshot() {
        UUID host = UUID.randomUUID();
        List<UUID> source = new ArrayList<>(List.of(host));
        RoundView view = new RoundView("ABC", host, "normal", false, source, Optional.empty());
        source.add(UUID.randomUUID());
        assertEquals(List.of(host), view.members());
        assertThrows(UnsupportedOperationException.class, () -> view.members().add(UUID.randomUUID()));
    }

    @Test
    void roundViewRejectsNullChallenge() {
        assertThrows(NullPointerException.class, () -> new RoundView("ABC", UUID.randomUUID(), "normal", false, List.of(), null));
    }

    @Test
    void challengeViewIsASnapshot() {
        UUID p = UUID.randomUUID();
        List<UUID> source = new ArrayList<>(List.of(p));
        ChallengeView view = new ChallengeView("id", "team", 3, 0L, source);
        source.clear();
        assertEquals(List.of(p), view.participants());
        assertThrows(UnsupportedOperationException.class, () -> view.participants().add(UUID.randomUUID()));
    }
}
