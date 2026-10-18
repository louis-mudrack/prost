package dev.louismudrack.prost.reward;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.catalogue.ChallengeDef;
import dev.louismudrack.prost.catalogue.DrawRules;
import dev.louismudrack.prost.catalogue.Rule;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.game.Kind;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.game.Situation;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.menu.ChoiceMenu;
import dev.louismudrack.prost.menu.HandoutMenu;
import dev.louismudrack.prost.menu.Items;
import dev.louismudrack.prost.menu.Menu;
import dev.louismudrack.prost.menu.RulesMenu;
import dev.louismudrack.prost.storage.Stats;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** What winners get - real life only (spec §4.5): hand out sips, make a rule, a joker, pick the next challenge. */
public final class Rewards implements Listener {
    /** an open hand-out: who may receive how much (room), what the giver assigned so far */
    public static final class Handout {
        public final Round round;
        final UUID giver;
        final int amount;
        public final Map<UUID, Integer> room;
        public final Map<UUID, Integer> assigned = new LinkedHashMap<>();
        BukkitTask timeout;
        long deadline;
        boolean done;

        Handout(Round round, UUID giver, int amount, Map<UUID, Integer> room) {
            this.round = round;
            this.giver = giver;
            this.amount = amount;
            this.room = room;
        }

        public int remaining() { return amount - assigned.values().stream().mapToInt(x -> x).sum(); }
    }

    private final ProstPlugin plugin;
    private final Random rnd = new Random();
    private final Map<UUID, Handout> handouts = new HashMap<>();
    private final Map<UUID, Menu> pendingMenus = new HashMap<>();
    /** read by the async chat thread */
    private final RuleInputs ruleInputs = new RuleInputs();

    public Rewards(ProstPlugin plugin) { this.plugin = plugin; }

    private long rewardTicks() { return plugin.cfg().rewardSeconds() * 20L; }

    /** a winner (or a Team's MVP for everyone) gets the entry's reward; several options = a choice, time up = the first */
    public void grant(Round r, UUID winner, ChallengeDef d, boolean team, List<UUID> teamMembers) {
        List<Reward> options = team ? RewardRules.teamOptions(d.rewards()) : d.rewards();
        Player p = Bukkit.getPlayer(winner);
        if (options.size() == 1 || p == null) {
            apply(r, winner, d, options.get(0), team, teamMembers);
            return;
        }
        boolean[] chosen = {false};
        Reward first = options.get(0);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (chosen[0]) return;
            chosen[0] = true;
            expired(winner, "Zeit abgelaufen - du bekommst: " + Msg.HL + label(first, d, true),
                    "Time's up - you get: " + Msg.HL + label(first, d, false));
            apply(r, winner, d, first, team, teamMembers);
        }, rewardTicks());
        List<ChoiceMenu.Option> list = new ArrayList<>();
        for (Reward rw : options) list.add(new ChoiceMenu.Option(icon(rw, d, Msg.de(winner)), () -> {
            if (chosen[0]) return;
            chosen[0] = true;
            timeout.cancel();
            settled(winner);
            apply(r, winner, d, rw, team, teamMembers);
        }));
        offer(p, new ChoiceMenu(plugin, p, "Belohnung wählen", "Pick your reward", list).until(deadline()));
    }

    /** a choice the winner may close by accident: the chat button (/prost belohnung) opens it again until it is decided */
    private void offer(Player p, Menu menu) {
        pendingMenus.put(p.getUniqueId(), menu);
        menu.open();
        p.sendMessage(Msg.mm(Msg.PREFIX + Msg.t(p.getUniqueId(),
                "<click:run_command:'/prost belohnung'><#55e07a>[Belohnung wählen]</click> <dark_gray>(öffnet das Menü wieder)",
                "<click:run_command:'/prost belohnung'><#55e07a>[Pick your reward]</click> <dark_gray>(opens the menu again)")));
    }

    private void settled(UUID winner) { pendingMenus.remove(winner); }

    /** the time ran out: close the still-open menu and say what was chosen - a later click would silently do nothing */
    private void expired(UUID winner, String de, String en) {
        Menu menu = pendingMenus.remove(winner);
        Player p = Bukkit.getPlayer(winner);
        if (p == null) return;
        if (menu != null && menu.isOpen()) p.closeInventory();
        Msg.tell(p, de, en);
    }

    private long deadline() { return System.currentTimeMillis() + plugin.cfg().rewardSeconds() * 1000L; }

    public boolean reopenChoice(Player p) {
        Menu menu = pendingMenus.get(p.getUniqueId());
        if (menu == null) return false;
        menu.open();
        return true;
    }

    private static String label(Reward rw, ChallengeDef d, boolean de) {
        return switch (rw) {
            case HANDOUT -> de ? d.give() + " Schlucke verteilen" : "Hand out " + d.give() + " sips";
            case RULE -> de ? "Regel aufstellen" : "Make a rule";
            case JOKER -> "Joker";
            case NEXT -> de ? "Nächste Herausforderung wählen" : "Pick the next challenge";
        };
    }

    private static ItemStack icon(Reward rw, ChallengeDef d, boolean de) {
        String name = Msg.HL + label(rw, d, de);
        return switch (rw) {
            case HANDOUT -> Items.item(Material.HONEY_BOTTLE, name, List.of());
            case RULE -> Items.item(Material.WRITABLE_BOOK, name, List.of());
            case JOKER -> Items.item(Material.NETHER_STAR, name, List.of("<gray>" + (de ? "Hebt einen Schluck-Befehl auf" : "Cancels one drink")));
            case NEXT -> Items.item(Material.COMPASS, name, List.of());
        };
    }

    private void apply(Round r, UUID winner, ChallengeDef d, Reward rw, boolean team, List<UUID> teamMembers) {
        switch (rw) {
            case HANDOUT -> handout(r, winner, d.give());
            case RULE -> rule(r, winner);
            case JOKER -> joker(r, team ? teamMembers : List.of(winner));
            case NEXT -> next(r, winner);
        }
    }

    // ---------------------------------------------------------------------------------------------- joker

    private void joker(Round r, List<UUID> ids) {
        for (UUID id : ids) {
            Member m = r.member(id);
            Player p = Bukkit.getPlayer(id);
            if (m == null) continue;
            if (RewardRules.jokerFits(m.jokers, plugin.cfg().jokerMax())) {
                m.jokers++;
                if (p != null) Msg.tell(p, "Du hast einen " + Msg.OK + "Joker<gray>! (" + m.jokers + ")", "You got a " + Msg.OK + "joker<gray>! (" + m.jokers + ")");
            } else if (p != null) {
                Msg.tell(p, "Du hast schon " + m.jokers + " Joker - der Joker verfällt.", "You already hold " + m.jokers + " jokers - this one lapses.");
            }
        }
        plugin.rounds().save();
    }

    // ---------------------------------------------------------------------------------------------- hand out

    private void handout(Round r, UUID giver, int amount) {
        Member g = r.member(giver);
        if (g == null) return;
        String name = Msg.esc(g.name);
        if (RewardRules.realLifeHandout(r.members.size())) {
            Msg.round(r, de -> Msg.HL + name + (de ? " <gray>verteilt " + Msg.HL + amount + " <gray>Schlucke in echt." : " <gray>hands out " + Msg.HL + amount + " <gray>sips in real life."));
            g.tally.given += amount;
            plugin.stats().add(g.id, g.name, Stats.Field.GIVEN, amount);
            plugin.rounds().save();
            return;
        }
        long now = System.currentTimeMillis();
        Map<UUID, Integer> room = new LinkedHashMap<>();
        for (UUID id : r.activeIds()) {
            if (id.equals(giver)) continue;
            Member m = r.member(id);
            int free = SipLedger.room(m.history, now, m.limit);
            if (free > 0) room.put(id, free);
        }
        Player p = Bukkit.getPlayer(giver);
        if (room.isEmpty()) {
            if (p != null) Msg.tell(p, "Gerade kann niemand Schlucke bekommen - sie verfallen.", "Nobody can receive sips right now - they lapse.");
            return;
        }
        Handout h = new Handout(r, giver, amount, room);
        handouts.put(giver, h);
        h.deadline = deadline();
        h.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> handoutExpired(h), rewardTicks());
        if (p == null) {
            finishHandout(h);
            return;
        }
        new HandoutMenu(plugin, p, this, h).until(h.deadline).open();
        p.sendMessage(Msg.mm(Msg.PREFIX + Msg.t(giver,
                "Verteile " + Msg.HL + amount + " <gray>Schlucke. <click:run_command:'/prost verteilen'><#55e07a>[Schlucke verteilen]</click>",
                "Hand out " + Msg.HL + amount + " <gray>sips. <click:run_command:'/prost verteilen'><#55e07a>[Hand out sips]</click>")));
    }

    public boolean reopenHandout(Player p) {
        Handout h = handouts.get(p.getUniqueId());
        if (h == null || h.done) return false;
        new HandoutMenu(plugin, p, this, h).until(h.deadline).open();
        return true;
    }

    private void handoutExpired(Handout h) {
        Player p = Bukkit.getPlayer(h.giver);
        if (p != null && !h.done) {
            if (p.getOpenInventory().getTopInventory().getHolder(false) instanceof HandoutMenu menu && menu.shows(h)) p.closeInventory();
            Msg.tell(p, "Zeit abgelaufen - die Schlucke wurden verteilt, der Rest zufällig.", "Time's up - the sips went out, the rest at random.");
        }
        finishHandout(h);
    }

    /** confirm, timeout, giver offline or shutdown: whatever is unassigned goes out at random, within everyone's room */
    public void finishHandout(Handout h) {
        if (h.done) return;
        h.done = true;
        if (h.timeout != null) h.timeout.cancel();
        handouts.remove(h.giver);
        Map<UUID, Integer> all = new LinkedHashMap<>(h.assigned);
        all.keySet().removeIf(id -> !canReceive(h.round, id));   // paused or gone since the picker opened (spec §3.3)
        all.values().removeIf(n -> n <= 0);
        int rest = h.amount - all.values().stream().mapToInt(n -> n).sum();
        if (rest > 0) {
            Map<UUID, Integer> left = new LinkedHashMap<>();
            for (Map.Entry<UUID, Integer> e : h.room.entrySet())
                if (canReceive(h.round, e.getKey())) left.put(e.getKey(), e.getValue() - all.getOrDefault(e.getKey(), 0));
            HandoutRules.randomRest(rest, left, rnd).forEach((id, n) -> all.merge(id, n, Integer::sum));
        }
        Member g = h.round.member(h.giver);
        String giverName = g == null ? "?" : g.name;
        int given = 0;
        for (Map.Entry<UUID, Integer> e : all.entrySet()) {
            plugin.sips().drink(h.round, e.getKey(), e.getValue(), SipLedger.Source.HANDOUT, "von " + giverName, "from " + giverName);
            given += e.getValue();
        }
        if (g != null) {
            g.tally.given += given;
            plugin.stats().add(g.id, g.name, Stats.Field.GIVEN, given);
        }
        plugin.rounds().save();   // the giver's tally changes after the receivers' saves
    }

    private static boolean canReceive(Round r, UUID id) { return r.isActive(id) && Bukkit.getPlayer(id) != null; }

    // ---------------------------------------------------------------------------------------------- rules

    private void rule(Round r, UUID winner) {
        Player p = Bukkit.getPlayer(winner);
        List<Rule> prepared = plugin.preparedRules();
        if (p == null) {
            if (!prepared.isEmpty()) addRule(r, winner, prepared.get(rnd.nextInt(prepared.size())));
            return;
        }
        boolean[] done = {false};
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (done[0] || prepared.isEmpty()) return;
            done[0] = true;
            expired(winner, "Zeit abgelaufen - eine zufällige Regel wurde gewählt.", "Time's up - a random rule was picked.");
            ruleInputs.close(winner);
            addRule(r, winner, prepared.get(rnd.nextInt(prepared.size())));
        }, rewardTicks());
        offer(p, new RulesMenu(plugin, p, prepared, index -> {
            if (done[0]) return;
            done[0] = true;
            timeout.cancel();
            settled(winner);
            addRule(r, winner, prepared.get(index));
        }, () -> {
            if (done[0]) return;
            done[0] = true;   // the typed rule replaces the timeout's random one; typing nothing in time = no rule
            timeout.cancel();
            settled(winner);
            ruleInputs.open(winner, r, deadline());
            Msg.tell(p, "Schreib deine Regel in den Chat (max. " + plugin.cfg().customRuleMaxLength() + " Zeichen) - \"abbrechen\" bricht ab.",
                    "Type your rule into chat (max " + plugin.cfg().customRuleMaxLength() + " characters) - \"cancel\" stops.");
        }).until(deadline()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        RuleInputs.Input in = ruleInputs.take(id, System.currentTimeMillis());
        if (in == null) return;   // no window, or it ran out: the message stays normal chat (Review Focus 2)
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            String rule = RewardRules.cleanRule(text, plugin.cfg().customRuleMaxLength());
            if (rule != null && in.round().member(id) != null) addRule(in.round(), id, new Rule(rule, rule));
        });
    }

    private void addRule(Round r, UUID maker, Rule rule) {
        Member m = r.member(maker);
        if (m == null) return;
        Player p = Bukkit.getPlayer(maker);
        if (r.rules.size() >= plugin.cfg().rulesMaxActive() && p != null) {
            offer(p, new RulesMenu(plugin, p, List.copyOf(r.rules), index -> {
                settled(maker);
                place(r, m, rule, index);
            }, null));
            return;
        }
        place(r, m, rule, -1);
    }

    /** index -1 = append (the oldest drops when full) */
    private void place(Round r, Member maker, Rule rule, int index) {
        if (index >= 0 && index < r.rules.size()) r.rules.set(index, rule);
        else r.addRule(rule, plugin.cfg().rulesMaxActive());
        maker.tally.rulesMade++;
        plugin.stats().add(maker.id, maker.name, Stats.Field.RULES, 1);
        String name = Msg.esc(maker.name), de = Msg.esc(rule.de()), en = Msg.esc(rule.en());
        Msg.round(r, g -> Msg.HL + name + (g ? " <gray>stellt eine Regel auf: <white>" + de : " <gray>makes a rule: <white>" + en));
        plugin.rounds().save();
    }

    // ---------------------------------------------------------------------------------------------- pick next

    private void next(Round r, UUID winner) {
        List<Player> online = new ArrayList<>();
        for (UUID id : r.activeIds()) { Player p = Bukkit.getPlayer(id); if (p != null) online.add(p); }
        List<DrawRules.Choice> cards = DrawRules.cards(plugin.challenges(), r.used, online.size(), Situation.of(online),
                plugin.cfg().kindWeights(), rnd, 3);
        Player p = Bukkit.getPlayer(winner);
        if (cards.isEmpty()) return;
        if (p == null) {
            DrawRules.Choice c = cards.get(rnd.nextInt(cards.size()));
            r.queued = new Round.Queued(c.def().id(), c.kind(), List.of());
            return;
        }
        boolean[] done = {false};
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (done[0]) return;
            done[0] = true;
            expired(winner, "Zeit abgelaufen - eine zufällige Herausforderung kommt als Nächstes.",
                    "Time's up - a random challenge comes next.");
            DrawRules.Choice c = cards.get(rnd.nextInt(cards.size()));
            r.queued = new Round.Queued(c.def().id(), c.kind(), List.of());
        }, rewardTicks());
        boolean de = Msg.de(winner);
        List<ChoiceMenu.Option> list = new ArrayList<>();
        for (DrawRules.Choice c : cards) {
            ItemStack card = Items.item(Material.MAP, Msg.HL + Msg.esc(c.def().name(de)), List.of(
                    "<gray>" + Msg.esc(c.def().text(de)), "<dark_gray>" + c.kind().key + " · " + c.def().sips() + (de ? " Schlucke" : " sips")));
            list.add(new ChoiceMenu.Option(card, () -> {
                if (done[0]) return;
                done[0] = true;
                timeout.cancel();
                settled(winner);
                choosePlayers(r, p, c);
            }));
        }
        offer(p, new ChoiceMenu(plugin, p, "Nächste Herausforderung", "Next challenge", list).until(deadline()));
    }

    /** Solo/Duel picked: the winner also names who plays (one for Solo, two picks for Duel) */
    private void choosePlayers(Round r, Player chooser, DrawRules.Choice c) {
        int needed = c.kind() == Kind.SOLO ? 1 : c.kind() == Kind.DUEL ? 2 : 0;
        if (needed == 0) {
            r.queued = new Round.Queued(c.def().id(), c.kind(), List.of());
            return;
        }
        pickPlayers(r, chooser, c, new ArrayList<>(), needed);
    }

    private void pickPlayers(Round r, Player chooser, DrawRules.Choice c, List<UUID> picked, int needed) {
        List<ChoiceMenu.Option> list = new ArrayList<>();
        for (UUID id : r.activeIds()) {
            if (picked.contains(id)) continue;
            Member m = r.member(id);
            list.add(new ChoiceMenu.Option(Items.head(id, Msg.HL + Msg.esc(m.name), List.of()), () -> {
                List<UUID> now = new ArrayList<>(picked);
                now.add(id);
                if (now.size() >= needed) {
                    settled(chooser.getUniqueId());
                    r.queued = new Round.Queued(c.def().id(), c.kind(), List.copyOf(now));
                    Msg.tell(chooser, "Gemerkt - kommt als Nächstes.", "Noted - it comes next.");
                } else pickPlayers(r, chooser, c, now, needed);
            }));
        }
        offer(chooser, new ChoiceMenu(plugin, chooser, "Wer spielt?", "Who plays?", list));
    }

    /** onDisable: open hand-outs go out at random now (inline) */
    public void flushAll() { for (Handout h : new ArrayList<>(handouts.values())) finishHandout(h); }
}
