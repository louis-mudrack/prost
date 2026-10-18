package dev.louismudrack.prost.drink;

import dev.louismudrack.prost.reward.HandoutRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static dev.louismudrack.prost.Checks.*;

class SipLedgerTest {
    @Test
    void ledger() {
        long h = SipLedger.HOUR_MS, now = 10 * h;
        List<SipLedger.Sip> hist = new ArrayList<>(List.of(new SipLedger.Sip(now - h, 5), new SipLedger.Sip(now - h + 1, 4)));
        eq("a sip exactly one hour old no longer counts", 4, SipLedger.recent(hist, now));
        eq("room under a limit of 20", 16, SipLedger.room(hist, now, 20));
        SipLedger.Applied a = SipLedger.apply(hist, now, 3, 20);
        eq("under the limit: all drunk", new SipLedger.Applied(3, 0), a);
        eq("old entries are dropped from the history", 2, hist.size());
        SipLedger.Applied b = SipLedger.apply(hist, now, 20, 20);
        eq("over the limit: the rest lapses", new SipLedger.Applied(13, 7), b);
        eq("at the limit: nothing more", new SipLedger.Applied(0, 2), SipLedger.apply(hist, now, 2, 20));
        eq("an hour later there is room again", 20, SipLedger.room(hist, now + h, 20));
        eq("a negative amount does nothing", new SipLedger.Applied(0, 0), SipLedger.apply(new ArrayList<>(), now, -3, 20));

        check("joker offered for a challenge penalty", SipLedger.offerJoker(1, SipLedger.Source.CHALLENGE, true));
        check("joker offered for the roulette", SipLedger.offerJoker(2, SipLedger.Source.ROULETTE, true));
        check("never for handed-out sips", !SipLedger.offerJoker(2, SipLedger.Source.HANDOUT, true));
        check("not without a joker", !SipLedger.offerJoker(0, SipLedger.Source.CHALLENGE, true));
        check("not to a player who just left (the prompt counts as declined)", !SipLedger.offerJoker(1, SipLedger.Source.CHALLENGE, false));

        Map<UUID, Integer> room = new HashMap<>(Map.of(A, 1, B, 5, C, 0));
        Map<UUID, Integer> rest = HandoutRules.randomRest(4, room, new Random(7));
        eq("all four sips placed", 4, rest.values().stream().mapToInt(x -> x).sum());
        check("nobody gets more than their room", rest.getOrDefault(A, 0) <= 1 && rest.getOrDefault(C, 0) == 0);
        eq("more sips than room: only the room is used", 6,
                HandoutRules.randomRest(9, new HashMap<>(Map.of(A, 1, B, 5)), new Random(1)).values().stream().mapToInt(x -> x).sum());
        eq("nobody has room: nothing placed", Map.of(), HandoutRules.randomRest(3, new HashMap<>(Map.of(A, 0)), new Random(1)));
    }
}
