package dev.louismudrack.prost.game;

import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.drink.SipLedger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Round <-> plain maps for rounds.yml. Decoding tolerates missing fields; members come back "away". */
final class RoundCodec {
    private RoundCodec() {}

    static Map<String, Object> encode(Round r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", r.code);
        m.put("host", r.host.toString());
        m.put("pace", r.pace.key);
        m.put("created", r.createdAt);
        m.put("paused", r.paused);
        m.put("challenges", r.challenges);
        m.put("used", new ArrayList<>(r.used));
        List<Map<String, String>> rules = new ArrayList<>();
        for (Rule rule : r.rules) rules.add(new LinkedHashMap<>(rule.texts()));
        m.put("rules", rules);
        Map<String, Object> members = new LinkedHashMap<>();
        for (Member x : r.members.values()) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("name", x.name);
            mm.put("limit", x.limit);
            mm.put("water", x.water);
            mm.put("self-paused", x.selfPaused);
            mm.put("joined", x.joinedAt);
            mm.put("jokers", x.jokers);
            mm.put("solos", x.solos);
            mm.put("duels", x.duels);
            List<List<Long>> hist = new ArrayList<>();
            for (SipLedger.Sip s : x.history) hist.add(List.of(s.at(), (long) s.amount()));
            mm.put("history", hist);
            mm.put("roulette", new ArrayList<>(x.rouletteHits));
            Tally t = x.tally;
            mm.put("tally", List.of(t.drunk, t.water, t.given, t.won, t.lost, t.roulette, t.jokersUsed, t.rulesMade));
            members.put(x.id.toString(), mm);
        }
        m.put("members", members);
        return m;
    }

    static Round decode(Map<?, ?> m, int defaultLimit) {
        Object code = m.get("code"), host = m.get("host");
        if (code == null || host == null) return null;
        Pace pace = Pace.of(String.valueOf(m.get("pace")));
        Round r = new Round(String.valueOf(code), UUID.fromString(String.valueOf(host)), pace == null ? Pace.NORMAL : pace, num(m.get("created")));
        r.paused = Boolean.TRUE.equals(m.get("paused"));
        r.challenges = (int) num(m.get("challenges"));
        if (m.get("used") instanceof List<?> used) for (Object u : used) r.used.add(String.valueOf(u));
        if (m.get("rules") instanceof List<?> rules)
            for (Object o : rules) if (o instanceof Map<?, ?> rm) {
                Map<String, String> texts = new LinkedHashMap<>();
                rm.forEach((k, v) -> { if (v != null) texts.put(String.valueOf(k), String.valueOf(v)); });
                if (!texts.isEmpty()) r.rules.add(new Rule(texts));
            }
        if (m.get("members") instanceof Map<?, ?> members) {
            for (Map.Entry<?, ?> e : members.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?> mm)) continue;
                UUID id = UUID.fromString(String.valueOf(e.getKey()));
                Object limit = mm.get("limit");
                Member x = r.add(id, String.valueOf(mm.get("name") == null ? "?" : mm.get("name")),
                        limit instanceof Number n ? n.intValue() : defaultLimit, Boolean.TRUE.equals(mm.get("water")), num(mm.get("joined")));
                x.selfPaused = Boolean.TRUE.equals(mm.get("self-paused"));
                x.jokers = (int) num(mm.get("jokers"));
                x.solos = (int) num(mm.get("solos"));
                x.duels = (int) num(mm.get("duels"));
                if (mm.get("history") instanceof List<?> hist)
                    for (Object h : hist) if (h instanceof List<?> pair && pair.size() == 2)
                        x.history.add(new SipLedger.Sip(num(pair.get(0)), (int) num(pair.get(1))));
                if (mm.get("roulette") instanceof List<?> hits) for (Object h : hits) x.rouletteHits.add(num(h));
                if (mm.get("tally") instanceof List<?> t && t.size() == 8) {
                    x.tally.drunk = (int) num(t.get(0)); x.tally.water = (int) num(t.get(1)); x.tally.given = (int) num(t.get(2));
                    x.tally.won = (int) num(t.get(3)); x.tally.lost = (int) num(t.get(4)); x.tally.roulette = (int) num(t.get(5));
                    x.tally.jokersUsed = (int) num(t.get(6)); x.tally.rulesMade = (int) num(t.get(7));
                }
                x.away = true;
                x.awaySince = System.currentTimeMillis();
            }
        }
        if (!r.members.containsKey(r.host) && !r.members.isEmpty()) r.host = r.members.keySet().iterator().next();
        return r;
    }

    private static long num(Object v) { return v instanceof Number n ? n.longValue() : 0L; }
}
