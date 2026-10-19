package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.command.ProstCommand;
import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.Items;
import dev.louismudrack.prost.reward.Rewards;
import dev.louismudrack.prost.tracker.LuckRules.Prize;
import dev.louismudrack.prost.ui.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * The luck games, decided in a menu: wheel, coin, chests, dice, high_low, boxes. Every participant who decides (the coin:
 * only the caller, the first drawn player) gets a menu with the clock; at the deadline a missing decision is made at
 * random, and once everybody has decided a reveal of luck.reveal-seconds cycles faces in the menus (the first half) and
 * then shows the result. Who drops out keeps nothing: a box is free again, a decision is out of the verdict. LuckRules
 * judges; every draw comes from ProstPlugin#random, every time from ProstPlugin#now.
 */
final class LuckTracker extends BaseTracker implements GameTracker {
    private static final long SECOND_MS = 1000L;
    private static final int LEFT = 11, MIDDLE = 13, RIGHT = 15, BELOW = 22;
    private static final int[] CHEST_SLOTS = {LEFT, MIDDLE, RIGHT};
    /** the coin's call and the high/low guess as a decision */
    private static final int HEADS = 1, TAILS = 0, HIGHER = 1, LOWER = 0, TWO_SIDES = 2;
    private static final float SPIN_PITCH = 1.6f;
    private static final Prize[] PRIZES = Prize.values();

    private final String mode;
    /** participants in the run's order: the verdict keeps it */
    private final List<UUID> order = new ArrayList<>();
    /** wheel and dice: the roll; coin: HEADS/TAILS; high_low: HIGHER/LOWER; chests and boxes: the index */
    private final Map<UUID, Integer> choices = new HashMap<>();
    private final Map<UUID, GameMenu> menus = new HashMap<>();
    private UUID caller;
    private boolean heads, revealing, buttonsTold;
    private int shown, next, trap, boxes;
    /** what each chest hides, filled when one is opened */
    private Prize[] chests;
    private long decideEndsAt, revealStartsAt, revealEndsAt;

    LuckTracker(TrackContext ctx) {
        super(ctx);
        mode = ctx.def().mode();
    }

    @Override
    protected void begin(List<Player> ps) {
        decideEndsAt = ctx.plugin().now() + ctx.def().seconds() * SECOND_MS;
        for (Player p : ps) order.add(p.getUniqueId());
        if (order.isEmpty()) return;
        caller = order.get(0);
        switch (mode) {
            case "coin" -> heads = rnd().nextBoolean();
            case "high_low" -> {
                shown = rnd().nextInt(LuckRules.NUMBER_MAX) + 1;
                next = LuckRules.nextNumber(shown, rnd());
            }
            case "boxes" -> {
                boxes = Math.min(order.size(), MenuSlots.MAX);
                trap = rnd().nextInt(boxes);
            }
            default -> { }
        }
        for (Player p : ps) if (decides(p.getUniqueId())) open(p);
    }

    @Override
    public long deadline() { return revealing ? revealEndsAt : decideEndsAt; }

    @Override
    public boolean decided() { return revealing && ctx.plugin().now() >= revealEndsAt; }

    @Override
    public Verdict verdict() {
        int sips = ctx.def().sips();
        Map<UUID, Integer> picks = picks();
        UUID first = order.get(0);
        return switch (mode) {
            case "wheel" -> LuckRules.wheel(ctx.kind(), picks, sips);
            case "coin" -> LuckRules.coin(caller, other(), picks.get(caller) == HEADS, heads, sips);
            case "chests" -> LuckRules.chests(first, chests[picks.get(first)], sips);
            case "dice" -> LuckRules.dice(first, picks.get(first), sips);
            case "high_low" -> LuckRules.highLow(guesses(picks), shown, next, sips);
            default -> LuckRules.boxes(picks, trap, sips);
        };
    }

    @Override
    public String status(UUID viewer, UUID who) {
        if (revealing) return Msg.str(viewer, "game.luck.revealing");
        boolean own = viewer.equals(who);
        if (!decides(who)) return own ? Msg.str(viewer, "game.luck.calls", V.of("name", name(caller))) : Msg.str(viewer, "game.luck.ready");
        if (choices.containsKey(who)) return Msg.str(viewer, own ? "game.luck.picked" : "game.luck.ready");
        return Msg.str(viewer, own ? "game.luck.choose" : "game.luck.choosing");
    }

    /** the rolls and the choices; a walkover before the reveal keeps the default line */
    @Override
    public String result(UUID viewer) {
        if (!revealing) return null;
        Map<UUID, Integer> picks = picks();
        UUID first = order.isEmpty() ? null : order.get(0);
        return switch (mode) {
            case "wheel" -> Msg.str(viewer, "game.luck.result.wheel", V.mm("rolls", each(picks, n -> String.valueOf(n))));
            case "coin" -> picks.containsKey(caller) ? Msg.str(viewer, "game.luck.result.coin", V.of("name", name(caller)),
                    V.mm("call", side(viewer, picks.get(caller) == HEADS)), V.mm("side", side(viewer, heads))) : null;
            case "chests" -> Msg.str(viewer, "game.luck.result.chests", V.of("name", name(first)), V.mm("prize", prizeName(viewer, chests[picks.get(first)])));
            case "dice" -> Msg.str(viewer, "game.luck.result.dice", V.of("name", name(first)), V.of("roll", picks.get(first)));
            case "high_low" -> Msg.str(viewer, "game.luck.result.high_low", V.of("shown", shown), V.of("next", next),
                    V.mm("guesses", each(picks, g -> Msg.str(viewer, g == HIGHER ? "game.luck.higher" : "game.luck.lower"))));
            default -> {
                UUID taker = takerOf(trap);
                yield taker == null ? Msg.str(viewer, "game.luck.result.boxes_free", V.of("box", trap + 1))
                        : Msg.str(viewer, "game.luck.result.boxes", V.of("box", trap + 1), V.of("name", name(taker)));
            }
        };
    }

    /** honest per game: a reward only comes when nobody drinks (Resolution.Outcome#rewarded) */
    @Override
    public String stakes(UUID viewer) {
        V sips = V.mm("sips", Msg.sips(viewer, ctx.def().sips())), give = V.mm("give", Msg.sips(viewer, ctx.def().give())),
                reward = V.mm("reward", Rewards.offered(viewer, ctx.def(), false));
        boolean solo = ctx.kind() == Kind.SOLO;
        return switch (mode) {
            case "wheel" -> solo ? Msg.str(viewer, "challenge.stakes.wheel_solo", sips, reward) : Msg.str(viewer, "challenge.stakes.wheel", sips);
            case "coin" -> Msg.str(viewer, "challenge.stakes.coin", sips);
            case "chests" -> Msg.str(viewer, "challenge.stakes.chests", sips, give);
            case "dice" -> Msg.str(viewer, "challenge.stakes.dice", sips, give);
            case "high_low" -> solo ? Msg.str(viewer, "challenge.stakes.high_low_solo", sips, reward) : Msg.str(viewer, "challenge.stakes.high_low", sips, reward);
            default -> Msg.str(viewer, "challenge.stakes.boxes", sips);
        };
    }

    /** out of the verdict: a box is free again; the others may all have decided now */
    @Override
    public void dropped(UUID id) {
        order.remove(id);
        choices.remove(id);
        GameMenu menu = menus.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (menu != null && p != null && menu.isOpen()) p.closeInventory();
        if (!revealing && !order.isEmpty() && everyoneDecided()) reveal();
        else redrawAll();
    }

    @Override
    public boolean reopen(Player p) {
        GameMenu menu = menus.get(p.getUniqueId());
        if (menu == null) return false;
        menu.open();
        return true;
    }

    @Override
    public void poll(List<Player> online) {
        if (!buttonsTold) tellButtons();
        if (!revealing && ctx.plugin().now() >= decideEndsAt) decideMissing();
        if (revealing) showFaces();
    }

    /** inline: onDisable stops the run through here */
    @Override
    public void stop() {
        super.stop();
        menus.forEach((id, menu) -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && menu.isOpen()) p.closeInventory();
        });
        menus.clear();
    }

    // ---------------------------------------------------------------------------------------------- deciding

    private boolean decides(UUID id) { return !"coin".equals(mode) || id.equals(caller); }

    private boolean everyoneDecided() { return order.stream().filter(id -> decides(id)).allMatch(id -> choices.containsKey(id)); }

    /** a click or the deadline; the wheel and the die roll here, a chest's prize is drawn when it is opened */
    private void decide(UUID id, int option) {
        if (revealing || choices.containsKey(id) || !order.contains(id) || !decides(id)) return;
        switch (mode) {
            case "wheel" -> choices.put(id, rnd().nextInt(LuckRules.WHEEL_MAX) + 1);
            case "dice" -> choices.put(id, rnd().nextInt(LuckRules.DIE_MAX) + 1);
            case "chests" -> {
                choices.put(id, option);
                fillChests(option);
            }
            case "boxes" -> {
                if (option < 0 || takerOf(option) != null) return;
                choices.put(id, option);
            }
            default -> choices.put(id, option);
        }
        if (everyoneDecided()) reveal();
        else redrawAll();
    }

    private int randomOption(UUID id) {
        return switch (mode) {
            case "coin", "high_low" -> rnd().nextInt(TWO_SIDES);
            case "chests" -> rnd().nextInt(CHEST_SLOTS.length);
            case "boxes" -> {
                List<Integer> free = new ArrayList<>();
                for (int b = 0; b < boxes; b++) if (takerOf(b) == null) free.add(b);
                yield free.isEmpty() ? -1 : free.get(rnd().nextInt(free.size()));
            }
            default -> 0;   // the wheel and the die roll in decide
        };
    }

    /** the deadline: who has not decided gets a random decision; then the reveal starts in any case */
    private void decideMissing() {
        for (UUID id : new ArrayList<>(order)) if (decides(id) && !choices.containsKey(id)) decide(id, randomOption(id));
        // more players than boxes leaves one without a box: that must not hold the game up
        if (!revealing) reveal();
    }

    /** the opened chest's prize first (one draw), the other two chests get the rest */
    private void fillChests(int opened) {
        List<Prize> rest = new ArrayList<>(List.of(PRIZES));
        Prize prize = rest.remove(rnd().nextInt(rest.size()));
        Collections.shuffle(rest, rnd());
        chests = new Prize[PRIZES.length];
        for (int i = 0; i < chests.length; i++) chests[i] = i == opened ? prize : rest.remove(0);
    }

    private void reveal() {
        revealing = true;
        revealStartsAt = ctx.plugin().now();
        revealEndsAt = revealStartsAt + ctx.plugin().cfg().games().luckRevealSeconds() * SECOND_MS;
        for (GameMenu menu : menus.values()) menu.until(0);   // decided: the clock would only count down to nothing
        redrawAll();
    }

    /** the second half of the reveal shows the real result, the first half random faces */
    private boolean settled() { return ctx.plugin().now() >= revealStartsAt + (revealEndsAt - revealStartsAt) / 2; }

    /** on the first poll: the run announces the challenge after starting the tracker, the button belongs after it */
    private void tellButtons() {
        buttonsTold = true;
        for (UUID id : menus.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) Msg.tell(p, "game.reopen", V.of("cmd", ProstCommand.RUN_PLAY));
        }
    }

    // ---------------------------------------------------------------------------------------------- menus

    private void open(Player p) {
        UUID id = p.getUniqueId();
        GameMenu menu = new GameMenu(ctx.plugin(), p, "game.luck.title." + mode, m -> draw(m, id));
        menu.until(decideEndsAt);
        menus.put(id, menu);
        menu.open();
    }

    private void redrawAll() { for (GameMenu menu : menus.values()) menu.redraw(); }

    private void showFaces() {
        boolean spinning = !settled();
        menus.forEach((id, menu) -> {
            if (!menu.isOpen()) return;
            menu.redraw();
            Player p = Bukkit.getPlayer(id);
            if (spinning && p != null) Sounds.tick(p, SPIN_PITCH);
        });
    }

    private void draw(GameMenu m, UUID viewer) {
        switch (mode) {
            case "wheel" -> drawRoll(m, viewer, Material.SUNFLOWER, "game.luck.spin", LuckRules.WHEEL_MAX);
            case "dice" -> drawRoll(m, viewer, Material.BONE, "game.luck.roll", LuckRules.DIE_MAX);
            case "coin" -> drawCoin(m, viewer);
            case "chests" -> drawChests(m, viewer);
            case "high_low" -> drawHighLow(m, viewer);
            default -> drawBoxes(m, viewer);
        }
    }

    /** the wheel and the die: one button; the number stays hidden until the reveal */
    private void drawRoll(GameMenu m, UUID viewer, Material button, String key, int sides) {
        Integer roll = choices.get(viewer);
        if (roll == null) m.button(MIDDLE, Items.item(button, "<hl>" + Msg.str(viewer, key), List.of()), choose(viewer, 0));
        else if (!revealing) m.button(MIDDLE, Items.item(button, Msg.str(viewer, "game.luck.picked"), List.of()), null);
        else {
            int face = settled() ? roll : rnd().nextInt(sides) + 1;
            m.button(MIDDLE, number(face, "<hl>" + face), null);
        }
    }

    private void drawCoin(GameMenu m, UUID viewer) {
        m.button(LEFT, coin(viewer, true, mark(viewer, HEADS)), choose(viewer, HEADS));
        m.button(RIGHT, coin(viewer, false, mark(viewer, TAILS)), choose(viewer, TAILS));
        if (revealing) m.button(MIDDLE, coin(viewer, settled() ? heads : rnd().nextBoolean(), List.of()), null);
    }

    /** the opened chest flickers through the prizes, then all three open */
    private void drawChests(GameMenu m, UUID viewer) {
        for (int i = 0; i < CHEST_SLOTS.length; i++) {
            ItemStack icon;
            if (revealing && settled()) icon = prize(viewer, chests[i], mark(viewer, i));
            else if (revealing && picked(viewer, i)) icon = prize(viewer, PRIZES[rnd().nextInt(PRIZES.length)], mark(viewer, i));
            else icon = Items.item(picked(viewer, i) ? Material.ENDER_CHEST : Material.CHEST,
                    "<hl>" + Msg.str(viewer, "game.luck.chest", V.of("n", i + 1)), mark(viewer, i));
            m.button(CHEST_SLOTS[i], icon, choose(viewer, i));
        }
    }

    private void drawHighLow(GameMenu m, UUID viewer) {
        m.button(MIDDLE, number(shown, Msg.str(viewer, "game.luck.shown", V.of("n", shown))), null);
        m.button(LEFT, Items.item(Material.RED_DYE, "<hl>" + Msg.str(viewer, "game.luck.lower"), mark(viewer, LOWER)), choose(viewer, LOWER));
        m.button(RIGHT, Items.item(Material.LIME_DYE, "<hl>" + Msg.str(viewer, "game.luck.higher"), mark(viewer, HIGHER)), choose(viewer, HIGHER));
        if (!revealing) return;
        int face = settled() ? next : LuckRules.nextNumber(shown, rnd());
        m.button(BELOW, number(face, Msg.str(viewer, "game.luck.next", V.of("n", face))), null);
    }

    /** a taken box shows its taker's head and cannot be picked; the reveal shakes a random box, then shows the trap */
    private void drawBoxes(GameMenu m, UUID viewer) {
        int shaken = revealing && !settled() ? rnd().nextInt(boxes) : -1;
        for (int b = 0; b < boxes; b++) {
            UUID taker = takerOf(b);
            String label = Msg.str(viewer, "game.luck.box", V.of("n", b + 1));
            List<String> takerLore = taker == null ? List.of() : List.of("<white>" + Msg.esc(name(taker)));
            ItemStack icon;
            if (revealing && b == (settled() ? trap : shaken)) icon = Items.item(Material.TNT, "<err>" + Msg.str(viewer, "game.luck.trap"), takerLore);
            else if (taker != null) icon = Items.head(taker, "<hl>" + label, takerLore);
            else icon = Items.item(Material.BARREL, "<hl>" + label, List.of());
            m.button(MenuSlots.of(b, boxes), icon, taker == null ? choose(viewer, b) : null);
        }
    }

    /** the click of an option, while the viewer may still decide */
    private Runnable choose(UUID viewer, int option) { return revealing || choices.containsKey(viewer) ? null : () -> decide(viewer, option); }

    private boolean picked(UUID viewer, int option) {
        Integer c = choices.get(viewer);
        return c != null && c == option;
    }

    private List<String> mark(UUID viewer, int option) { return picked(viewer, option) ? List.of(Msg.str(viewer, "game.luck.your_pick")) : List.of(); }

    /** a number to read at a glance: the stack shows it too */
    private static ItemStack number(int n, String nameMini) {
        ItemStack it = Items.item(Material.PAPER, nameMini, List.of());
        it.setAmount(n);
        return it;
    }

    private static ItemStack coin(UUID viewer, boolean heads, List<String> lore) {
        return Items.item(heads ? Material.GOLD_NUGGET : Material.IRON_NUGGET, "<hl>" + side(viewer, heads), lore);
    }

    private ItemStack prize(UUID viewer, Prize prize, List<String> lore) {
        Material icon = switch (prize) {
            case DRINK -> Material.POTION;
            case JOKER -> Material.NETHER_STAR;
            case HANDOUT -> Material.HONEY_BOTTLE;
        };
        return Items.item(icon, "<hl>" + prizeName(viewer, prize), lore);
    }

    // ---------------------------------------------------------------------------------------------- text

    private static String side(UUID viewer, boolean heads) { return Msg.str(viewer, heads ? "game.luck.heads" : "game.luck.tails"); }

    private String prizeName(UUID viewer, Prize prize) {
        int n = prize == Prize.HANDOUT ? ctx.def().give() : ctx.def().sips();
        return Msg.str(viewer, "game.luck.prize." + prize.name().toLowerCase(Locale.ROOT), V.mm("sips", Msg.sips(viewer, n)));
    }

    /** "Anna 7 · Ben 3": each name escaped, then what {@code value} makes of the decision */
    private String each(Map<UUID, Integer> picks, IntFunction<String> value) {
        StringJoiner j = new StringJoiner(" <dark_gray>· ");
        picks.forEach((id, pick) -> j.add("<white>" + Msg.esc(name(id)) + " <hl>" + value.apply(pick)));
        return j.toString();
    }

    private String name(UUID id) {
        Member m = id == null ? null : ctx.round().member(id);
        return m == null ? "?" : m.name;
    }

    // ---------------------------------------------------------------------------------------------- state

    private Random rnd() { return ctx.plugin().random(); }

    /** the decisions of who is still in, in the run's order */
    private Map<UUID, Integer> picks() {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (UUID id : order) if (choices.containsKey(id)) out.put(id, choices.get(id));
        return out;
    }

    private static Map<UUID, Boolean> guesses(Map<UUID, Integer> picks) {
        Map<UUID, Boolean> out = new LinkedHashMap<>();
        picks.forEach((id, guess) -> out.put(id, guess == HIGHER));
        return out;
    }

    private UUID other() { return order.stream().filter(id -> !id.equals(caller)).findFirst().orElseThrow(); }

    private UUID takerOf(int box) {
        for (Map.Entry<UUID, Integer> e : choices.entrySet()) if (e.getValue() == box) return e.getKey();
        return null;
    }
}
