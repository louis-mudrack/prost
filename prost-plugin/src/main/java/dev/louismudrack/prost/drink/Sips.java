package dev.louismudrack.prost.drink;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.api.SipSource;
import dev.louismudrack.prost.api.event.SipsEvent;
import dev.louismudrack.prost.command.ProstCommand;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.storage.StatsField;
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
import java.util.function.Function;

/**
 * Everything a member drinks goes through here (spec §9): a joker prompt where it applies, then the ledger (personal
 * limit, rest lapses), the tally, the stats and one line to the round. A member who leaves while a prompt is open
 * simply has the sips recorded when it runs out (Review Focus 1).
 */
public final class Sips {
    private record Pending(Round round, UUID id, int amount, SipLedger.Source source, Function<UUID, String> why, BukkitTask timeout) {}

    private final ProstPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public Sips(ProstPlugin plugin) { this.plugin = plugin; }

    /** {@code why} words the reason in each viewer's language, as MiniMessage: names and catalogue text in it escaped */
    public void drink(Round r, UUID id, int amount, SipLedger.Source source, Function<UUID, String> why) {
        Member m = r.member(id);
        if (m == null || amount <= 0) return;
        Pending earlier = pending.remove(id);
        if (earlier != null) {   // one prompt at a time: the earlier one counts as declined
            if (earlier.timeout() != null) earlier.timeout().cancel();   // or it would close the new prompt early
            settle(earlier);
        }
        Player p = Bukkit.getPlayer(id);
        if (!SipLedger.offerJoker(m.jokers, source, p != null)) {
            settle(new Pending(r, id, amount, source, why, null));
            return;
        }
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending x = pending.remove(id);
            if (x != null) settle(x);
        }, plugin.cfg().jokerOfferSeconds() * 20L);
        pending.put(id, new Pending(r, id, amount, source, why, timeout));
        int left = m.jokers;
        Msg.tell(p, "joker.offer", V.mm("sips", Msg.sips(id, amount)), V.of("left", left), V.of("cmd", ProstCommand.RUN_JOKER));
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
        plugin.stats().add(m.id, m.name, StatsField.JOKERS, 1);
        Msg.round(x.round(), "joker.used", V.of("name", m.name));
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
        plugin.stats().add(m.id, m.name, m.water ? StatsField.WATER : StatsField.DRUNK, a.drunk());
        int drunk = a.drunk();
        Msg.roundEach(x.round(), viewer -> Msg.str(viewer, m.water ? "sips.drinks_water" : "sips.drinks", V.of("name", m.name), V.mm("sips", Msg.sips(viewer, drunk)), V.mm("why", x.why().apply(viewer)))
                + (a.lapsed() > 0 ? " " + Msg.str(viewer, "sips.limit_tag") : ""));
        if (drunk > 0) Bukkit.getPluginManager().callEvent(new SipsEvent(m.id, x.round().code, drunk, source(x.source()), m.water));
        Player p = Bukkit.getPlayer(m.id);
        if (p == null) {
            plugin.rounds().save();
            return;
        }
        if (a.lapsed() > 0) Msg.tell(p, "sips.limit_reached", V.mm("lapsed", Msg.sips(m.id, a.lapsed())));
        if (drunk > 0) {
            p.showTitle(Title.title(Msg.mm("<primary>" + Msg.sips(m.id, drunk) + "</primary>"),
                    Msg.mm("<gray>" + x.why().apply(m.id)),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(400))));
            Sounds.fail(p);
        }
        plugin.rounds().save();
    }

    private static SipSource source(SipLedger.Source s) {
        return switch (s) {
            case CHALLENGE -> SipSource.CHALLENGE;
            case ROULETTE -> SipSource.ROULETTE;
            case HANDOUT -> SipSource.HANDOUT;
        };
    }
}
