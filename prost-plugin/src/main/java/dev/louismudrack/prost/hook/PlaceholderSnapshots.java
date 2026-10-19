package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.game.ChallengeRun;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TAB and hologram plugins resolve %prost_*% off the main thread, where the rounds must not be read: the main thread
 * copies each online player's values once a tick, and the expansion reads only that copy.
 */
public final class PlaceholderSnapshots {
    /** the tag is a gradient rendered per player; once a second is enough for a text that changes with the language */
    private static final long TAG_EVERY_TICKS = 20;

    public record Snapshot(boolean inRound, String roundCode, int roundPlayers, String challenge, String challengeTime,
                           int sipsSession, int jokers, String limitLeft, String tag) {}

    private final ProstPlugin plugin;
    private final Map<UUID, Snapshot> byPlayer = new ConcurrentHashMap<>();
    private long ticks;

    public PlaceholderSnapshots(ProstPlugin plugin) { this.plugin = plugin; }

    /** null for a player who is not online (or not yet copied) */
    public Snapshot of(UUID id) { return byPlayer.get(id); }

    /** main thread only */
    public void publish() {
        boolean renderTags = ticks++ % TAG_EVERY_TICKS == 0;
        Set<UUID> online = new HashSet<>();
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            online.add(id);
            Snapshot old = byPlayer.get(id);
            byPlayer.put(id, copy(id, now, renderTags || old == null ? tag(id) : old.tag()));
        }
        byPlayer.keySet().retainAll(online);
    }

    private Snapshot copy(UUID id, long now, String tag) {
        Round r = plugin.rounds().of(id);
        Member m = r == null ? null : r.member(id);
        if (m == null) return new Snapshot(false, "", 0, "", "", 0, 0, "", tag);
        ChallengeRun run = plugin.director().running(r);
        return new Snapshot(true, r.code, r.members.size(),
                run == null ? "" : run.def.name(Msg.lang(id)),
                run == null ? "" : run.clock(),
                m.tally.drunk, m.jokers, String.valueOf(SipLedger.room(m.history, now, m.limit)), tag);
    }

    private static String tag(UUID id) {
        return LegacyComponentSerializer.legacySection().serialize(Msg.mm(Msg.str(id, "placeholder.tag")));
    }
}
