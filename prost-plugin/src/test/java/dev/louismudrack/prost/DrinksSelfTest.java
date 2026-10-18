package dev.louismudrack.prost;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Pure rules of AniDrinks (no Bukkit on the classpath): run by host/build.sh and host/selftest-local.sh. */
final class DrinksSelfTest {
    private static int passed, failed;

    static void check(String name, boolean ok) { if (ok) passed++; else { failed++; System.out.println("FAIL: " + name); } }
    static void eq(String name, Object want, Object got) { check(name + " (want " + want + ", got " + got + ")", Objects.equals(want, got)); }

    @org.junit.jupiter.api.Test void allChecks() {
        catalogueParsing();
        catalogueRules();
        rulesFile();
        resolution();
        ledger();
        roulette();
        draw();
        config();
        rounds();
        joinRules();
        avoidModes();
        rewardRules();
        summaryRules();
        ruleInputs();
        fmt();
        org.junit.jupiter.api.Assertions.assertEquals(0, failed, failed + " checks failed - the FAIL lines above name them");
    }

    static void fmt() {
        eq("one sip, German", "1 Schluck", Fmt.sips(1, true));
        eq("three sips, German", "3 Schlucke", Fmt.sips(3, true));
        eq("one sip, English", "1 sip", Fmt.sips(1, false));
        eq("three sips, English", "3 sips", Fmt.sips(3, false));
    }

    static void catalogueParsing() {
        Map<String, Object> iron = Map.of(
                "name", Map.of("de", "Eisenfieber", "en", "Iron fever"),
                "text", Map.of("de", "Baue {amount} Eisenerz ab", "en", "Mine {amount} iron ore"),
                "tracker", "mine", "targets", List.of("IRON_ORE", "DEEPSLATE_IRON_ORE"),
                "amount", 16, "seconds", 360, "sips", 3,
                "kinds", List.of("each", "race", "duel", "solo", "team"), "rewards", List.of("handout"));
        Map<String, Object> blaze = Map.ofEntries(   // Map.of stops at 10 pairs
                Map.entry("name", Map.of("de", "Feuerteufel", "en", "Blaze it")),
                Map.entry("text", Map.of("de", "Töte {amount} Lohen", "en", "Kill {amount} blazes")),
                Map.entry("tracker", "stat"), Map.entry("stat", "KILL_ENTITY"), Map.entry("targets", List.of("BLAZE")),
                Map.entry("amount", 2), Map.entry("seconds", 600), Map.entry("sips", 4), Map.entry("give", 3),
                Map.entry("kinds", List.of("each")), Map.entry("rewards", List.of("handout", "joker")),
                Map.entry("needs", List.of("nether")), Map.entry("also-avoid", List.of("explosion-damage")),
                Map.entry("min-players", 2), Map.entry("weight", 3));
        Map<String, Object> broken = Map.of("name", Map.of("de", "x", "en", "x"), "kinds", List.of("party"), "sips", 2);
        CatalogueParser.Parsed p = CatalogueParser.parse(Map.of("mine_iron", iron, "kill_blaze", blaze, "broken", broken, "junk", "text"));
        eq("two good entries parse", 2, p.defs().size());
        eq("two broken entries are reported, not fatal", 2, p.problems().size());
        check("the problem names the entry", p.problems().stream().anyMatch(s -> s.startsWith("broken:")));
        ChallengeDef d = p.defs().stream().filter(x -> x.id().equals("mine_iron")).findFirst().orElseThrow();
        eq("text fills the amount (DE)", "Baue 16 Eisenerz ab", d.text(true));
        eq("text fills the amount (EN)", "Mine 16 iron ore", d.text(false));
        eq("give defaults to sips", 3, d.give());
        eq("five kinds in file order", List.of(Kind.EACH, Kind.RACE, Kind.DUEL, Kind.SOLO, Kind.TEAM), d.kinds());
        eq("no needs", Set.of(), d.needs());
        eq("min players default 1", 1, d.minPlayers());
        eq("weight default 1", 1, d.weight());
        ChallengeDef b = p.defs().stream().filter(x -> x.id().equals("kill_blaze")).findFirst().orElseThrow();
        eq("give read", 3, b.give());
        eq("needs read", Set.of(Need.NETHER), b.needs());
        eq("also-avoid read", List.of("explosion-damage"), b.alsoAvoid());
        eq("rewards read", List.of(Reward.HANDOUT, Reward.JOKER), b.rewards());
        eq("stat read", "KILL_ENTITY", b.stat());
        eq("min players read", 2, b.minPlayers());

        eq("a tag resolves to its names", List.of("ZOMBIE", "HUSK", "DROWNED", "ZOMBIE_VILLAGER"), Tags.resolve("#zombies"));
        eq("a plain name stays itself", List.of("BLAZE"), Tags.resolve("BLAZE"));
        eq("an unknown tag is null", null, Tags.resolve("#nope"));
        eq("resolveAll flattens", 5, Tags.resolveAll(List.of("#zombies", "CREEPER")).size());
        eq("resolveAll fails on an unknown tag", null, Tags.resolveAll(List.of("#zombies", "#nope")));
        eq("deepslate ore counts as its ore kind", "IRON_ORE", Tags.distinctKey("DEEPSLATE_IRON_ORE"));
        eq("deepslate itself stays deepslate", "DEEPSLATE", Tags.distinctKey("DEEPSLATE"));
        eq("16 wool colours", 16, Tags.resolve("#wool").size());

        eq("kill stats count entities", StatTypes.Target.ENTITY, StatTypes.KNOWN.get("KILL_ENTITY"));
        eq("craft stats count items", StatTypes.Target.MATERIAL, StatTypes.KNOWN.get("CRAFT_ITEM"));
        eq("jumps have no target", StatTypes.Target.NONE, StatTypes.KNOWN.get("JUMP"));
        eq("a comma list of stats", List.of("WALK_ONE_CM", "SPRINT_ONE_CM"), StatTypes.split("WALK_ONE_CM, SPRINT_ONE_CM"));
        eq("distances are shown in blocks", 100, StatTypes.scale("WALK_ONE_CM"));
        eq("crouch time is shown in seconds", 20, StatTypes.scale("SNEAK_TIME"));
        eq("counts stay counts", 1, StatTypes.scale("JUMP"));
    }

    static ChallengeDef def(String tracker, String stat, String source, String mode, String value, List<String> targets,
                            List<Kind> kinds, Set<Need> needs, List<String> alsoAvoid, int sips, int seconds) {
        return new ChallengeDef("t_" + tracker, "Name", "Name", "Text", "Text", tracker, stat, source, mode, value, targets, 1,
                seconds, sips, sips, kinds, List.of(Reward.HANDOUT), needs, alsoAvoid, 1, 1);
    }

    static final CatalogueRules.Names FAKE_API = new CatalogueRules.Names() {
        public boolean material(String n) { return !n.startsWith("NOT_") && !n.equals("BLAZE") && !n.equals("ZOMBIE"); }
        public boolean entity(String n) { return !n.startsWith("NOT_") && !n.endsWith("_ORE"); }
        public boolean statistic(String n) { return StatTypes.KNOWN.containsKey(n); }
        public boolean item(String n) { return !n.equals("WATER"); }
        public boolean block(String n) { return !n.equals("STICK"); }
    };

    static void catalogueRules() {
        List<Kind> each = List.of(Kind.EACH);
        eq("a plain mine entry is valid", List.of(), CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("IRON_ORE"), each, Set.of(), List.of(), 3, 360), FAKE_API));
        check("an unknown tracker is reported", !CatalogueRules.validate(
                def("fly", "", "", "", "", List.of(), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("sips above 6 are reported", !CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("IRON_ORE"), each, Set.of(), List.of(), 7, 360), FAKE_API).isEmpty());
        check("sips below 1 are reported", !CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("IRON_ORE"), each, Set.of(), List.of(), 0, 360), FAKE_API).isEmpty());
        check("more than 15 minutes is reported", !CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("IRON_ORE"), each, Set.of(), List.of(), 3, 901), FAKE_API).isEmpty());
        check("a misspelled material is reported", !CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("NOT_AN_ORE"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("mine without targets is reported", !CatalogueRules.validate(
                def("mine", "", "", "", "", List.of(), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("a crafting stat on a block-only material is reported (Player.getStatistic would throw every poll)", !CatalogueRules.validate(
                def("stat", "CRAFT_ITEM", "", "", "", List.of("WATER"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("a mining stat on an item-only material is reported", !CatalogueRules.validate(
                def("stat", "MINE_BLOCK", "", "", "", List.of("STICK"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        eq("a crafting stat on an item is fine", List.of(), CatalogueRules.validate(
                def("stat", "CRAFT_ITEM", "", "", "", List.of("STICK"), each, Set.of(), List.of(), 3, 360), FAKE_API));
        eq("kill stats take entities", List.of(), CatalogueRules.validate(
                def("stat", "KILL_ENTITY", "", "", "", List.of("#zombies"), each, Set.of(), List.of(), 3, 360), FAKE_API));
        check("an entity stat with a material target is reported", !CatalogueRules.validate(
                def("stat", "KILL_ENTITY", "", "", "", List.of("IRON_ORE"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("an untyped stat with targets is reported", !CatalogueRules.validate(
                def("stat", "JUMP", "", "", "", List.of("IRON_ORE"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("an unknown stat is reported", !CatalogueRules.validate(
                def("stat", "FLY_ONE_CM_X", "", "", "", List.of(), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("an unknown tag is reported", !CatalogueRules.validate(
                def("collect", "", "", "", "", List.of("#nope"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("avoid with an unknown mode is reported", !CatalogueRules.validate(
                def("avoid", "", "", "fly", "", List.of(), each, Set.of(), List.of(), 2, 300), FAKE_API).isEmpty());
        eq("avoid with two modes is valid", List.of(), CatalogueRules.validate(
                def("avoid", "", "", "sleep,damage", "", List.of(), each, Set.of(), List.of(), 2, 300), FAKE_API));
        check("avoid as a race is reported (nobody can finish first)", !CatalogueRules.validate(
                def("avoid", "", "", "jump", "", List.of(), List.of(Kind.RACE), Set.of(), List.of(), 2, 300), FAKE_API).isEmpty());
        check("an unknown also-avoid is reported", !CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("IRON_ORE"), each, Set.of(), List.of("fly"), 3, 360), FAKE_API).isEmpty());
        check("reach below needs a number", !CatalogueRules.validate(
                def("reach", "", "", "below", "deep", List.of(), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("distinct with an unknown source is reported", !CatalogueRules.validate(
                def("distinct", "", "smell", "", "", List.of(), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        eq("distinct biomes need no targets", List.of(), CatalogueRules.validate(
                def("distinct", "", "biome", "", "", List.of(), each, Set.of(), List.of(), 3, 360), FAKE_API));
        check("proximity needs a numeric radius", !CatalogueRules.validate(
                def("proximity", "", "", "all", "near", List.of(), List.of(Kind.TEAM), Set.of(), List.of(), 2, 180), FAKE_API).isEmpty());

        // the owner's rule: Nether/End-only things must declare that they need it
        eq("quartz is Nether-only", Need.NETHER, DimensionLocks.required("NETHER_QUARTZ_ORE"));
        eq("shulker shells are End-only", Need.END, DimensionLocks.required("SHULKER_SHELL"));
        eq("ender pearls drop in the overworld too", null, DimensionLocks.required("ENDER_PEARL"));
        check("quartz without needs: nether is reported", !CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("NETHER_QUARTZ_ORE"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        eq("quartz with needs: nether is fine", List.of(), CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("NETHER_QUARTZ_ORE"), each, Set.of(Need.NETHER), List.of(), 3, 360), FAKE_API));
        check("a blaze kill without needs: nether is reported", !CatalogueRules.validate(
                def("stat", "KILL_ENTITY", "", "", "", List.of("BLAZE"), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
        check("entering the End without needs: end is reported", !CatalogueRules.validate(
                def("reach", "", "", "dimension", "the_end", List.of(), each, Set.of(), List.of(), 3, 360), FAKE_API).isEmpty());
    }

    static void rulesFile() {
        List<Rule> rules = RulesFile.parse(List.of(Map.of("de", "Niemand sagt \"ich\".", "en", "Nobody says \"I\"."),
                Map.of("de", "nur deutsch"), "junk"));
        eq("only complete rules count", 1, rules.size());
        eq("the language is picked", "Nobody says \"I\".", rules.get(0).text(false));
        eq("a non-list file gives no rules", List.of(), RulesFile.parse("text"));
    }

    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    static Resolution.Progress pr(UUID id, int value, boolean done, long doneAt) { return new Resolution.Progress(id, value, done, false, doneAt); }

    static void resolution() {
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

        // one penalty per challenge (owner, test game 2026-10-17): when anyone drinks, the winners get no reward on top
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

    static void ledger() {
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

    static void roulette() {
        long min = 60_000L;
        RouletteRules.Settings s = new RouletteRules.Settings(Map.of("block", 0.003, "kill", 0.02), 3, 30 * min, 4 * min,
                new int[]{40, 30, 20, 10});
        check("no hits yet: not capped", !RouletteRules.capped(List.of(), 100 * min, s));
        check("inside the 4-minute gap: capped", RouletteRules.capped(List.of(97 * min), 100 * min, s));
        check("after the gap: free", !RouletteRules.capped(List.of(95 * min), 100 * min, s));
        check("three hits in 30 minutes: capped", RouletteRules.capped(List.of(75 * min, 82 * min, 90 * min), 100 * min, s));
        check("the oldest left the window: free again", !RouletteRules.capped(List.of(70 * min, 82 * min, 90 * min), 100 * min, s));
        List<Long> hits = new ArrayList<>(List.of(60 * min, 82 * min));
        RouletteRules.prune(hits, 100 * min, 30 * min);
        eq("prune keeps only the window", List.of(82 * min), hits);

        check("a roll under the chance triggers", RouletteRules.triggers(s, "kill", 0.019));
        check("a roll at the chance does not", !RouletteRules.triggers(s, "kill", 0.02));
        check("an unknown category never triggers", !RouletteRules.triggers(s, "jump", 0.0));

        eq("lowest roll = 1 sip", 1, RouletteRules.spin(s.weights(), 0.0));
        eq("just under 40 % = still 1", 1, RouletteRules.spin(s.weights(), 0.399));
        eq("40 % = 2", 2, RouletteRules.spin(s.weights(), 0.40));
        eq("highest roll = 4", 4, RouletteRules.spin(s.weights(), 0.9999));
        int[] counts = new int[5];
        Random r = new Random(42);
        for (int i = 0; i < 100_000; i++) counts[RouletteRules.spin(s.weights(), r.nextDouble())]++;
        check("over 100 000 spins the shares match 40/30/20/10 within 1 %",
                Math.abs(counts[1] - 40_000) < 1_000 && Math.abs(counts[2] - 30_000) < 1_000
                        && Math.abs(counts[3] - 20_000) < 1_000 && Math.abs(counts[4] - 10_000) < 1_000);
    }

    static ChallengeDef entry(String id, List<Kind> kinds, Set<Need> needs, String tracker, String mode, String value, int minPlayers) {
        return new ChallengeDef(id, id, id, id, id, tracker, "", "", mode, value, List.of(), 1, 300, 2, 2, kinds,
                List.of(Reward.HANDOUT), needs, List.of(), minPlayers, 1);
    }

    static void draw() {
        DrawRules.Conditions all = new DrawRules.Conditions(true, true, true, true, true, false, false);
        DrawRules.Conditions closed = new DrawRules.Conditions(false, false, false, false, false, false, false);
        ChallengeDef quartz = entry("mine_quartz", List.of(Kind.EACH, Kind.SOLO), Set.of(Need.NETHER), "mine", "", "", 1);
        ChallengeDef end = entry("enter_end", List.of(Kind.SOLO), Set.of(Need.END), "reach", "dimension", "the_end", 1);
        ChallengeDef enterNether = entry("enter_nether", List.of(Kind.SOLO), Set.of(Need.NETHER), "reach", "dimension", "nether", 1);
        ChallengeDef zombies = entry("kill_zombies", List.of(Kind.TEAM, Kind.SOLO), Set.of(Need.NIGHT), "stat", "", "", 1);
        ChallengeDef day = entry("sun", List.of(Kind.SOLO), Set.of(Need.DAY), "stat", "", "", 1);
        ChallengeDef meet = entry("meet_up", List.of(Kind.TEAM), Set.of(), "proximity", "all", "5", 2);
        ChallengeDef logs = entry("collect_logs", List.of(Kind.TEAM, Kind.EACH, Kind.RACE, Kind.DUEL, Kind.SOLO), Set.of(), "collect", "", "", 1);

        // the owner's rule: only what is possible right now
        check("Nether challenge while the Nether is open", DrawRules.feasible(quartz, all));
        check("Nether challenge while it is closed: never", !DrawRules.feasible(quartz, closed));
        check("End challenge while the End is closed: never",
                !DrawRules.feasible(end, new DrawRules.Conditions(true, false, true, false, false, false, false)));
        check("enter the Nether while someone is already there: skipped",
                !DrawRules.feasible(enterNether, new DrawRules.Conditions(true, true, false, false, false, true, false)));
        check("night challenge at night", DrawRules.feasible(zombies, all));
        check("night challenge by day: never", !DrawRules.feasible(zombies, closed));
        check("day challenge at night: never", !DrawRules.feasible(day, all));
        check("day challenge by day", DrawRules.feasible(day, closed));
        check("rain needed but dry: never",
                !DrawRules.feasible(entry("rain", List.of(Kind.SOLO), Set.of(Need.RAIN), "stat", "", "", 1), closed));

        eq("one active member: solo only", List.of(Kind.SOLO), DrawRules.kinds(logs, 1));
        eq("two members: every kind", List.of(Kind.TEAM, Kind.EACH, Kind.RACE, Kind.DUEL, Kind.SOLO), DrawRules.kinds(logs, 2));
        eq("a 2-player entry never in a solo round", List.of(), DrawRules.kinds(meet, 1));

        List<ChallengeDef> pool = List.of(quartz, end, zombies, logs, meet);
        List<DrawRules.Choice> soloClosed = DrawRules.eligible(pool, Set.of(), 1, closed);
        check("solo, everything closed, day: only the logs remain",
                soloClosed.stream().allMatch(c -> c.def() == logs && c.kind() == Kind.SOLO) && soloClosed.size() == 1);
        check("used entries are not drawn again", DrawRules.eligible(pool, Set.of("collect_logs"), 1, closed).isEmpty());
        DrawRules.Draw reset = DrawRules.draw(pool, Set.of("collect_logs"), 1, closed, Map.of(Kind.SOLO, 15), new Random(1));
        check("an empty pool resets instead of stopping", reset != null && reset.poolReset() && reset.choice().def() == logs);
        check("nothing possible at all: no draw", DrawRules.draw(List.of(meet), Set.of(), 1, closed, Map.of(Kind.TEAM, 20), new Random(1)) == null);

        Map<Kind, Integer> onlyTeam = Map.of(Kind.TEAM, 1, Kind.EACH, 0, Kind.RACE, 0, Kind.DUEL, 0, Kind.SOLO, 0);
        for (int i = 0; i < 50; i++)
            eq("weight 0 kinds are never drawn", Kind.TEAM, DrawRules.draw(List.of(logs), Set.of(), 3, all, onlyTeam, new Random(i)).choice().kind());
        List<DrawRules.Choice> cards = DrawRules.cards(pool, Set.of(), 3, all, Map.of(Kind.TEAM, 1, Kind.EACH, 1, Kind.SOLO, 1), new Random(3), 3);
        eq("three cards", 3, cards.size());
        eq("three different challenges", 3L, cards.stream().map(c -> c.def().id()).distinct().count());

        Map<UUID, Integer> solos = Map.of(A, 2, B, 0, C, 1);
        eq("solo goes to the member with the fewest", B, DrawRules.fewest(solos, List.of(A, B, C), new Random(1)));
        eq("a duel takes the two with the fewest", Set.of(B, C), Set.copyOf(DrawRules.fewestTwo(solos, List.of(A, B, C), new Random(1))));
        eq("members never counted yet count as 0", A, DrawRules.fewest(Map.of(B, 1), List.of(A, B), new Random(1)));

        check("start when the time has come", DirectorRules.mayStart(false, false, 2, 100, 100));
        check("never a second challenge while one runs (/prost weiter too)", !DirectorRules.mayStart(true, false, 2, 100, 0));
        check("/prost weiter is refused while one runs", !DirectorRules.mayDrawNow(true));
        check("not while the round is paused", !DirectorRules.mayStart(false, true, 2, 100, 0));
        check("not with nobody active", !DirectorRules.mayStart(false, false, 0, 100, 0));
        check("not before the time", !DirectorRules.mayStart(false, false, 2, 99, 100));
        eq("next challenge = reward time + gap", 1_000L + 60_000L + 500_000L, DirectorRules.afterRun(1_000L, 60, 500_000L));
        eq("after a pause at least the minimum wait", 160_000L, DirectorRules.afterResume(100_000L, 50_000L, 60_000L));
        check("13000 is night", DirectorRules.isNight(13_000));
        check("22999 is still night", DirectorRules.isNight(22_999));
        check("23000 is dawn (spec: night = 13000-23000)", !DirectorRules.isNight(23_000));
        check("noon is day", !DirectorRules.isNight(6_000));
        check("day 2 night counts too", DirectorRules.isNight(24_000 + 15_000));

        eq("pace keys", Pace.PARTY, Pace.of("party"));
        eq("unknown pace", null, Pace.of("crazy"));
        eq("gap at the low end", 240_000L, Pace.gapMs(new int[]{240, 420}, 0.0));
        eq("gap at the high end", 420_000L, Pace.gapMs(new int[]{240, 420}, 1.0));
    }

    static void config() {
        DrinksConfig d = DrinksConfig.DEFAULTS;
        eq("max players", 8, d.maxPlayers());
        eq("warm-up", 120, d.warmupSeconds());
        eq("party pace window", "[240, 420]", java.util.Arrays.toString(d.pace().get(Pace.PARTY)));
        eq("kind weight each", 25, d.kindWeights().get(Kind.EACH));
        eq("joker max", 2, d.jokerMax());
        eq("limit default", 20, d.limitDefault());
        eq("roulette window 30 min", 30 * 60_000L, d.roulette().windowMs());
        eq("roulette gap 4 min", 4 * 60_000L, d.roulette().minGapMs());
        eq("roulette weights", "[40, 30, 20, 10]", java.util.Arrays.toString(d.roulette().weights()));
        eq("block chance", 0.003, d.roulette().chances().get("block"));
        eq("rules max", 5, d.rulesMaxActive());

        DrinksConfig c = DrinksConfig.parse(Map.of("max-players", 4, "pace", Map.of("party", List.of(60, 120)),
                "limit", Map.of("default", 10, "min", 5, "max", 40), "roulette", Map.of("max-hits", 5, "chances", Map.of("block", 0.01))));
        eq("override max players", 4, c.maxPlayers());
        eq("override one pace, keep the others", "[60, 120]", java.util.Arrays.toString(c.pace().get(Pace.PARTY)));
        eq("other paces keep defaults", "[480, 720]", java.util.Arrays.toString(c.pace().get(Pace.NORMAL)));
        eq("override a chance", 0.01, c.roulette().chances().get("block"));
        eq("unlisted chances keep defaults", 0.02, c.roulette().chances().get("kill"));
        eq("override max hits", 5, c.roulette().maxHits());

        DrinksConfig bad = DrinksConfig.parse(Map.of("max-players", "lots", "pace", Map.of("chill", List.of(900, 100)),
                "limit", Map.of("min", 50, "max", 10)));
        eq("a word instead of a number keeps the default", 8, bad.maxPlayers());
        eq("a reversed window keeps the default", "[720, 1080]", java.util.Arrays.toString(bad.pace().get(Pace.CHILL)));
        eq("min above max keeps the defaults", 5, bad.limitMin());
        eq("clamp below", 5, DrinksConfig.DEFAULTS.clampLimit(1));
        eq("clamp above", 40, DrinksConfig.DEFAULTS.clampLimit(99));

        eq("clock", "2:05", Fmt.clock(125_000));
        eq("clock rounds up a started second", "0:01", Fmt.clock(1));
        eq("clock never negative", "0:00", Fmt.clock(-500));
    }

    static void rounds() {
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
        for (int i = 0; i < 6; i++) rr.addRule(new Rule("R" + i, "R" + i), 5);
        eq("rules stay at the maximum", 5, rr.rules.size());
        eq("the oldest rule made room", "R1", rr.rules.get(0).de());

        String code = Round.newCode(new Random(5), Set.of());
        check("codes are 4 characters without look-alikes", code.length() == 4 && code.chars().noneMatch(ch -> "0O1I".indexOf(ch) >= 0));

        Round s = new Round("AB34", A, Pace.CHILL, 7);
        Member m = s.add(A, "Kasheyy", 25, true, 7);
        m.jokers = 2; m.solos = 1; m.history.add(new SipLedger.Sip(100, 3)); m.rouletteHits.add(200L); m.tally.drunk = 9;
        s.rules.add(new Rule("Linke Hand", "Left hand"));
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
        eq("rules survive", "Left hand", back.rules.get(0).en());
        check("used challenges survive", back.used.contains("mine_iron"));
        check("after a restart everyone is away until they rejoin", back.member(A).away);
        Round partial = RoundCodec.decode(Map.of("code", "QQ77", "host", A.toString(), "members", Map.of(A.toString(), Map.of("name", "X"))), 20);
        eq("a member with missing fields gets the default limit", 20, partial.member(A).limit);
        eq("missing pace = normal", Pace.NORMAL, partial.pace);
        eq("no host = unusable", null, RoundCodec.decode(Map.of("code", "QQ77"), 20));
    }

    static void joinRules() {
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

    static void avoidModes() {
        eq("one mode", Set.of("jump"), AvoidModes.parse("jump"));
        eq("a comma list with blanks", Set.of("sleep", "damage"), AvoidModes.parse("sleep, damage"));
        eq("empty = nothing banned", Set.of(), AvoidModes.parse(""));
    }

    static void rewardRules() {
        eq("a team never hands out", List.of(Reward.RULE, Reward.NEXT), RewardRules.teamOptions(List.of(Reward.HANDOUT, Reward.RULE, Reward.NEXT)));
        eq("an entry with only hand-out gives a team a joker each", List.of(Reward.JOKER), RewardRules.teamOptions(List.of(Reward.HANDOUT)));
        check("a joker fits below the maximum", RewardRules.jokerFits(1, 2));
        check("no third joker", !RewardRules.jokerFits(2, 2));
        check("a solo round hands out in real life", RewardRules.realLifeHandout(1));
        check("a group hands out in the game", !RewardRules.realLifeHandout(3));
        eq("a typed rule is trimmed", "Nur flüstern", RewardRules.cleanRule("  Nur flüstern ", 60));
        eq("colour codes are stripped", "Rot", RewardRules.cleanRule("§cRot", 60));
        eq("too long is cut", "abcde", RewardRules.cleanRule("abcdefgh", 5));
        eq("'abbrechen' cancels", null, RewardRules.cleanRule("abbrechen", 60));
        eq("'cancel' cancels", null, RewardRules.cleanRule("Cancel", 60));
        eq("only blanks cancel", null, RewardRules.cleanRule("   ", 60));
    }

    static void summaryRules() {
        Round r = new Round("SS11", A, Pace.NORMAL, 0);
        Member a = r.add(A, "a", 20, false, 1), b = r.add(B, "b", 20, false, 2), c = r.add(C, "c", 20, false, 3);
        a.tally.won = 2; b.tally.won = 3; c.tally.won = 3;
        a.tally.drunk = 9;
        eq("most wins, tie = who joined first", B, SummaryRules.top(r.members.values(), m -> m.tally.won));
        eq("most sips", A, SummaryRules.top(r.members.values(), m -> m.tally.drunk + m.tally.water));
        eq("nobody handed out = no title", null, SummaryRules.top(r.members.values(), m -> m.tally.given));
    }

    /** review finding 1: an own-rule window that ran out must not swallow the player's next chat message */
    static void ruleInputs() {
        RuleInputs in = new RuleInputs();
        Round r = new Round("RI11", A, Pace.NORMAL, 0);
        in.open(A, r, 1_000);
        eq("a rule typed in time is taken", r, in.take(A, 900).round());
        eq("an input is taken once", null, in.take(A, 900));
        in.open(B, r, 1_000);
        eq("typed after the time ran out: not taken, the message stays chat", null, in.take(B, 1_001));
        eq("and the stale input is gone", null, in.take(B, 500));
        eq("no input: nothing taken", null, in.take(C, 0));
    }
}
