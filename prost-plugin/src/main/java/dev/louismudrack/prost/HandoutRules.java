package dev.louismudrack.prost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Sips a winner did not assign before the picker closed go to random eligible members (spec §4.5), within their limit. */
final class HandoutRules {
    private HandoutRules() {}

    static Map<UUID, Integer> randomRest(int rest, Map<UUID, Integer> room, Random rnd) {
        Map<UUID, Integer> left = new LinkedHashMap<>(room);
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < rest; i++) {
            List<UUID> open = new ArrayList<>();
            for (Map.Entry<UUID, Integer> e : left.entrySet()) if (e.getValue() > 0) open.add(e.getKey());
            if (open.isEmpty()) break;
            UUID pick = open.get(rnd.nextInt(open.size()));
            left.merge(pick, -1, Integer::sum);
            out.merge(pick, 1, Integer::sum);
        }
        return out;
    }
}
