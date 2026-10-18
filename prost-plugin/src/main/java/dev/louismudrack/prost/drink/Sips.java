package dev.louismudrack.prost.drink;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.storage.Stats;
import dev.louismudrack.prost.ui.Sounds;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a member drinks goes through here (spec §9): a joker prompt where it applies, then the ledger (personal
 * limit, rest lapses), the tally, the stats and one line to the round. A member who leaves while a prompt is open
 * simply has the sips recorded when it runs out (Review Focus 1).
 */
public final class Sips {
    private record Pending(Round round, UUID id, int amount, SipLedger.Source source, String whyDe, String whyEn, BukkitTask timeout) {}

    private final ProstPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public Sips(ProstPlugin plugin) { this.plugin = plugin; }

    public void drink(Round r, UUID id, int amount, SipLedger.Source source, String whyDe, String whyEn) {
        Member m = r.member(id);
        if (m == null || amount <= 0) return;
        Pending earlier = pending.remove(id);
        if (earlier != null) {   // one prompt at a time: the earlier one counts as declined
            if (earlier.timeout() != null) earlier.timeout().cancel();   // or it would close the new prompt early
            settle(earlier);
        }
        Player p = Bukkit.getPlayer(id);
        if (!SipLedger.offerJoker(m.jokers, source, p != null)) {
            settle(new Pending(r, id, amount, source, whyDe, whyEn, null));
            return;
        }
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending x = pending.remove(id);
            if (x != null) settle(x);
        }, plugin.cfg().jokerOfferSeconds() * 20L);
        pending.put(id, new Pending(r, id, amount, source, whyDe, whyEn, timeout));
        int left = m.jokers;
        p.sendMessage(Msg.mm(Msg.PREFIX + Msg.t(id,
                "Du müsstest " + Msg.HL + amount + " <gray>Schlucke trinken. <click:run_command:'/prost joker'><#55e07a>[Joker einsetzen]</click> <dark_gray>(" + left + " übrig)",
                "You would drink " + Msg.HL + amount + " <gray>sips. <click:run_command:'/prost joker'><#55e07a>[Use a joker]</click> <dark_gray>(" + left + " left)")));
        Sounds.bell(p);
    }

    public boolean useJoker(Player p) {
        Pending x = pending.remove(p.getUniqueId());
        if (x == null) return false;
        if (x.timeout() != null) x.timeout().cancel();
        Member m = x.round().member(x.id());
        if (m == null || m.jokers <= 0) {
            settle(x);
            return true;
        }
        m.jokers--;
        m.tally.jokersUsed++;
        plugin.stats().add(m.id, m.name, Stats.Field.JOKERS, 1);
        String name = Msg.esc(m.name);
        Msg.round(x.round(), de -> Msg.HL + name + (de ? " <gray>setzt einen Joker ein - kein Schluck." : " <gray>uses a joker - no sip."));
        Sounds.success(p);
        plugin.rounds().save();
        return true;
    }

    /** onDisable: every open prompt counts as declined, inline (no scheduler while disabling) */
    public void flushAll() {
        for (Pending x : new ArrayList<>(pending.values())) {
            if (x.timeout() != null) x.timeout().cancel();
            settle(x);
        }
        pending.clear();
    }

    private void settle(Pending x) {
        Member m = x.round().member(x.id());
        if (m == null) return;   // left the round meanwhile
        SipLedger.Applied a = SipLedger.apply(m.history, System.currentTimeMillis(), x.amount(), m.limit);
        if (m.water) m.tally.water += a.drunk();
        else m.tally.drunk += a.drunk();
        plugin.stats().add(m.id, m.name, m.water ? Stats.Field.WATER : Stats.Field.DRUNK, a.drunk());
        String name = Msg.esc(m.name), whyDe = Msg.esc(x.whyDe()), whyEn = Msg.esc(x.whyEn());
        int drunk = a.drunk();
        String limit = a.lapsed() > 0 ? " <dark_gray>(Limit)" : "";   // the same word in both languages
        Msg.round(x.round(), de -> Msg.HL + name + " <gray>" + (de
                ? "trinkt " + Msg.HL + drunk + " <gray>" + (m.water ? "Schlucke Wasser" : "Schlucke") + " <dark_gray>(" + whyDe + ")" + limit
                : "drinks " + Msg.HL + drunk + " <gray>" + (m.water ? "sips of water" : "sips") + " <dark_gray>(" + whyEn + ")" + limit));
        Player p = Bukkit.getPlayer(m.id);
        if (p == null) {
            plugin.rounds().save();
            return;
        }
        if (a.lapsed() > 0) Msg.tell(p, "Limit erreicht - " + a.lapsed() + " Schlucke verfallen.", "Limit reached - " + a.lapsed() + " sips lapse.");
        if (drunk > 0) {
            p.showTitle(Title.title(Msg.mm(Msg.t(m.id, "<#ff8f3f>" + drunk + " Schlucke", "<#ff8f3f>" + drunk + " sips")),
                    Msg.mm("<gray>" + Msg.t(m.id, whyDe, whyEn)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(400))));
            Sounds.fail(p);
        }
        plugin.rounds().save();
    }
}
