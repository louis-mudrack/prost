package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.command.ProstCommand;
import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.menu.GameMenu;
import dev.louismudrack.prost.menu.Items;
import dev.louismudrack.prost.reward.Rewards;
import dev.louismudrack.prost.ui.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * The luck games, decided in a menu; one class per game (wheel, coin, chests, dice, high_low, boxes). Every participant
 * who decides (the coin: only the caller, the first drawn player) gets a menu with the clock; at the deadline a missing
 * decision is made at random, and once everybody has decided a reveal of luck.reveal-seconds cycles faces in the menus
 * (the first half) and then shows the result. Who drops out keeps nothing: a box is free again, a decision is out of the
 * verdict. LuckRules judges; every draw comes from ProstPlugin#random, every time from ProstPlugin#now.
 */
abstract class LuckTracker extends BaseTracker implements GameTracker {
    private static final long SECOND_MS = 1000L;
    protected static final int LEFT = 11, MIDDLE = 13, RIGHT = 15, BELOW = 22;
    private static final float SPIN_PITCH = 1.6f;

    private final String mode;
    /** participants in the run's order: the verdict keeps it */
    protected final List<UUID> order = new ArrayList<>();
    /** wheel and dice: the roll; coin: HEADS/TAILS; high_low: HIGHER/LOWER; chests and boxes: the index */
    protected final Map<UUID, Integer> choices = new HashMap<>();
    private final Map<UUID, GameMenu> menus = new HashMap<>();
    /** the first drawn player: the coin's caller, the player of the solo games */
    protected UUID first;
    private boolean revealing, buttonsTold;
    private long decideEndsAt, revealStartsAt, revealEndsAt;

    LuckTracker(TrackContext ctx) {
        super(ctx);
        mode = ctx.def().mode();
    }

    /** CatalogueRules has already rejected unknown modes at load */
    static LuckTracker create(TrackContext ctx) {
        String mode = ctx.def().mode();
        return switch (mode) {
            case "wheel" -> new LuckWheel(ctx);
            case "coin" -> new LuckCoin(ctx);
            case "chests" -> new LuckChests(ctx);
            case "dice" -> new LuckDice(ctx);
            case "high_low" -> new LuckHighLow(ctx);
            case "boxes" -> new LuckBoxes(ctx);
            default -> throw new IllegalStateException("luck mode " + mode);
        };
    }

    // ---------------------------------------------------------------------------------------------- what a game decides

    /** the game's own draws at the start, before the menus open */
    protected void setUp() { }

    /** who gets a menu and a say */
    protected boolean decides(UUID id) { return true; }

    /** the status of a participant who does not decide: the viewer's own line or somebody else's */
    protected String watching(UUID viewer, boolean own) { return Msg.str(viewer, "game.luck.ready"); }

    /** stores a click or a random option; false refuses it (the decision stays open) */
    protected boolean accept(UUID id, int option) {
        choices.put(id, option);
        return true;
    }

    /** the option for who has not decided at the deadline */
    protected abstract int randomOption(UUID id);

    protected abstract Verdict judge(Map<UUID, Integer> picks, int sips);

    /** the rolls and the choices as the result line; null keeps the default line */
    protected abstract String line(UUID viewer, Map<UUID, Integer> picks);

    protected abstract void draw(GameMenu m, UUID viewer);

    /** who dropped out, with the decision they had made */
    protected void released(UUID id, Integer choice) { }

    // ---------------------------------------------------------------------------------------------- the run

    @Override
    protected void begin(List<Player> ps) {
        decideEndsAt = ctx.plugin().now() + ctx.def().seconds() * SECOND_MS;
        for (Player p : ps) order.add(p.getUniqueId());
        if (order.isEmpty()) return;
        first = order.get(0);
        setUp();
        for (Player p : ps) if (decides(p.getUniqueId())) open(p);
    }

    @Override
    public long deadline() { return revealing ? revealEndsAt : decideEndsAt; }

    @Override
    public boolean decided() { return revealing && ctx.plugin().now() >= revealEndsAt; }

    @Override
    public Verdict verdict() {
        if (order.isEmpty()) return Verdict.of(Map.of(), List.of());
        return judge(picks(), ctx.def().sips());
    }

    @Override
    public String status(UUID viewer, UUID who) {
        if (revealing) return Msg.str(viewer, "game.luck.revealing");
        boolean own = viewer.equals(who);
        if (!decides(who)) return watching(viewer, own);
        if (choices.containsKey(who)) return Msg.str(viewer, own ? "game.luck.picked" : "game.luck.ready");
        return Msg.str(viewer, own ? "game.luck.choose" : "game.luck.choosing");
    }

    /** a walkover before the reveal keeps the default line */
    @Override
    public String result(UUID viewer) { return revealing ? line(viewer, picks()) : null; }

    /** out of the verdict: a box is free again; the others may all have decided now */
    @Override
    public void dropped(UUID id) {
        order.remove(id);
        released(id, choices.remove(id));
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

    private boolean everyoneDecided() { return order.stream().filter(id -> decides(id)).allMatch(id -> choices.containsKey(id)); }

    /** a click or the deadline */
    protected final void decide(UUID id, int option) {
        if (revealing || choices.containsKey(id) || !order.contains(id) || !decides(id)) return;
        if (!accept(id, option)) return;
        if (everyoneDecided()) reveal();
        else redrawAll();
    }

    /** the deadline: who has not decided gets a random decision; then the reveal starts in any case */
    private void decideMissing() {
        for (UUID id : new ArrayList<>(order)) if (decides(id) && !choices.containsKey(id)) decide(id, randomOption(id));
        // more players than boxes leaves one without a box: that must not hold the game up
        if (!revealing) reveal();
    }

    private void reveal() {
        revealing = true;
        revealStartsAt = ctx.plugin().now();
        revealEndsAt = revealStartsAt + ctx.plugin().cfg().games().luckRevealSeconds() * SECOND_MS;
        for (GameMenu menu : menus.values()) menu.until(0);   // decided: the clock would only count down to nothing
        redrawAll();
    }

    protected final boolean revealing() { return revealing; }

    /** the second half of the reveal shows the real result, the first half random faces */
    protected final boolean settled() { return ctx.plugin().now() >= revealStartsAt + (revealEndsAt - revealStartsAt) / 2; }

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

    protected final void redrawAll() { for (GameMenu menu : menus.values()) menu.redraw(); }

    private void showFaces() {
        boolean spinning = !settled();
        menus.forEach((id, menu) -> {
            if (!menu.isOpen()) return;
            menu.redraw();
            Player p = Bukkit.getPlayer(id);
            if (spinning && p != null) Sounds.tick(p, SPIN_PITCH);
        });
    }

    /** the wheel and the die: one button; the number stays hidden until the reveal */
    protected final void drawRoll(GameMenu m, UUID viewer, Material button, String key, int sides) {
        Integer roll = choices.get(viewer);
        if (roll == null) m.button(MIDDLE, Items.item(button, "<hl>" + Msg.str(viewer, key), List.of()), choose(viewer, 0));
        else if (!revealing) m.button(MIDDLE, Items.item(button, Msg.str(viewer, "game.luck.picked"), List.of()), null);
        else {
            int face = settled() ? roll : rnd().nextInt(sides) + 1;
            m.button(MIDDLE, number(face, "<hl>" + face), null);
        }
    }

    /** the click of an option, while the viewer may still decide */
    protected final Runnable choose(UUID viewer, int option) { return revealing || choices.containsKey(viewer) ? null : () -> decide(viewer, option); }

    protected final boolean picked(UUID viewer, int option) {
        Integer c = choices.get(viewer);
        return c != null && c == option;
    }

    protected final List<String> mark(UUID viewer, int option) { return picked(viewer, option) ? List.of(Msg.str(viewer, "game.luck.your_pick")) : List.of(); }

    /** a number to read at a glance: the stack shows it too */
    protected static ItemStack number(int n, String nameMini) {
        ItemStack it = Items.item(Material.PAPER, nameMini, List.of());
        it.setAmount(n);
        return it;
    }

    // ---------------------------------------------------------------------------------------------- text

    /** "Anna 7 · Ben 3": each name escaped, then what {@code value} makes of the decision */
    protected final String each(Map<UUID, Integer> picks, IntFunction<String> value) {
        StringJoiner j = new StringJoiner(" <dark_gray>· ");
        picks.forEach((id, pick) -> j.add("<white>" + Msg.esc(name(id)) + " <hl>" + value.apply(pick)));
        return j.toString();
    }

    protected final V sips(UUID viewer) { return V.mm("sips", Msg.sips(viewer, ctx.def().sips())); }

    protected final V give(UUID viewer) { return V.mm("give", Msg.sips(viewer, ctx.def().give())); }

    protected final V reward(UUID viewer) { return V.mm("reward", Rewards.offered(viewer, ctx.def(), false)); }

    protected final boolean solo() { return ctx.kind() == Kind.SOLO; }

    // ---------------------------------------------------------------------------------------------- state

    protected final Random rnd() { return ctx.plugin().random(); }

    /** the decisions of who is still in, in the run's order */
    private Map<UUID, Integer> picks() {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (UUID id : order) if (choices.containsKey(id)) out.put(id, choices.get(id));
        return out;
    }
}
