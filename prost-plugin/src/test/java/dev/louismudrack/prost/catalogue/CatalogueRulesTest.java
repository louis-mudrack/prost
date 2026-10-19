package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.game.DirectorRules;
import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.game.Pace;
import dev.louismudrack.prost.reward.Reward;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static dev.louismudrack.prost.Checks.*;

class CatalogueRulesTest {
    @Test
    void catalogueParsing() {
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
        eq("text fills the amount (DE)", "Baue 16 Eisenerz ab", d.text("de"));
        eq("text fills the amount (EN)", "Mine 16 iron ore", d.text("en"));
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
        return new ChallengeDef("t_" + tracker, Map.of("de", "Name", "en", "Name"), Map.of("de", "Text", "en", "Text"), tracker, stat, source, mode, value, targets, 1,
                seconds, sips, sips, kinds, List.of(Reward.HANDOUT), needs, alsoAvoid, 1, 1);
    }

    static final CatalogueRules.Names FAKE_API = new CatalogueRules.Names() {
        public boolean material(String n) { return !n.startsWith("NOT_") && !n.equals("BLAZE") && !n.equals("ZOMBIE"); }
        public boolean entity(String n) { return !n.startsWith("NOT_") && !n.endsWith("_ORE"); }
        public boolean statistic(String n) { return StatTypes.KNOWN.containsKey(n); }
        public boolean item(String n) { return !n.equals("WATER"); }
        public boolean block(String n) { return !n.equals("STICK"); }
    };

    @Test
    void catalogueRules() {
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

    private static final List<Kind> REACTION_OR_SOLO = List.of(Kind.REACTION, Kind.SOLO);

    @Test
    void reactionNeedsAKnownModeAndReactionOrSoloKinds() {
        eq("a crouch reaction is valid", List.of(), CatalogueRules.validate(
                def("reaction", "", "", "crouch", "", List.of(), REACTION_OR_SOLO, Set.of(), List.of(), 1, 15), FAKE_API));
        eq("a dance is no reaction mode", List.of("unknown reaction mode 'dance'"), CatalogueRules.validate(
                def("reaction", "", "", "dance", "", List.of(), REACTION_OR_SOLO, Set.of(), List.of(), 1, 15), FAKE_API));
        eq("a reaction as a race is reported", List.of("reaction cannot be played as race"), CatalogueRules.validate(
                def("reaction", "", "", "crouch", "", List.of(), List.of(Kind.RACE), Set.of(), List.of(), 1, 15), FAKE_API));
        eq("the reaction modes", Set.of("crouch", "water", "chat", "look_up", "jump", "hold", "sit", "eat", "freeze"),
                CatalogueRules.REACTION_MODES);
    }

    @Test
    void reactionHoldNeedsMaterials() {
        eq("hold without targets", List.of("targets missing"), CatalogueRules.validate(
                def("reaction", "", "", "hold", "", List.of(), REACTION_OR_SOLO, Set.of(), List.of(), 2, 60), FAKE_API));
        eq("hold dirt is valid", List.of(), CatalogueRules.validate(
                def("reaction", "", "", "hold", "", List.of("DIRT"), REACTION_OR_SOLO, Set.of(), List.of(), 2, 60), FAKE_API));
        eq("hold a misspelled material", List.of("unknown material NOT_DIRT"), CatalogueRules.validate(
                def("reaction", "", "", "hold", "", List.of("NOT_DIRT"), REACTION_OR_SOLO, Set.of(), List.of(), 2, 60), FAKE_API));
    }

    @Test
    void theReactionKindNeedsAGameTracker() {
        eq("mine as a reaction", List.of("kind reaction does not work with tracker mine"), CatalogueRules.validate(
                def("mine", "", "", "", "", List.of("IRON_ORE"), List.of(Kind.REACTION), Set.of(), List.of(), 3, 360), FAKE_API));
        // also-avoid wraps a progress tracker; around a game it would hide the game from the run
        eq("a reaction with also-avoid", List.of("also-avoid does not work with tracker reaction"), CatalogueRules.validate(
                def("reaction", "", "", "crouch", "", List.of(), REACTION_OR_SOLO, Set.of(), List.of("jump"), 1, 15), FAKE_API));
    }

    @Test
    void potatoOnlyAsReaction() {
        eq("a hot potato as a reaction is valid", List.of(), CatalogueRules.validate(
                def("potato", "", "", "", "", List.of(), List.of(Kind.REACTION), Set.of(), List.of(), 3, 120), FAKE_API));
        eq("a hot potato for each player", List.of("potato cannot be played as each"), CatalogueRules.validate(
                def("potato", "", "", "", "", List.of(), List.of(Kind.EACH), Set.of(), List.of(), 3, 120), FAKE_API));
        eq("a hot potato with also-avoid", List.of("also-avoid does not work with tracker potato"), CatalogueRules.validate(
                def("potato", "", "", "", "", List.of(), List.of(Kind.REACTION), Set.of(), List.of("jump"), 3, 120), FAKE_API));
    }

    @Test
    void rulesFile() {
        List<Rule> rules = RulesFile.parse(List.of(Map.of("de", "Niemand sagt \"ich\".", "en", "Nobody says \"I\"."),
                Map.of("de", "nur deutsch"), Map.of("de", " "), "junk"));
        eq("rules with any text count, blank and junk items do not", 2, rules.size());
        eq("the language is picked", "Nobody says \"I\".", rules.get(0).text("en"));
        eq("a missing language falls back", "nur deutsch", rules.get(1).text("en"));
        eq("a non-list file gives no rules", List.of(), RulesFile.parse("text"));
    }

    static ChallengeDef entry(String id, List<Kind> kinds, Set<Need> needs, String tracker, String mode, String value, int minPlayers) {
        return new ChallengeDef(id, Map.of("en", id), Map.of("en", id), tracker, "", "", mode, value, List.of(), 1, 300, 2, 2, kinds,
                List.of(Reward.HANDOUT), needs, List.of(), minPlayers, 1);
    }

    @Test
    void draw() {
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

    @Test
    void avoidModes() {
        eq("one mode", Set.of("jump"), AvoidModes.parse("jump"));
        eq("a comma list with blanks", Set.of("sleep", "damage"), AvoidModes.parse("sleep, damage"));
        eq("empty = nothing banned", Set.of(), AvoidModes.parse(""));
    }
}
