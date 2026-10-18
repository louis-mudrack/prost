package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.drink.SipLedger;
import dev.louismudrack.prost.game.ChallengeRun;
import dev.louismudrack.prost.game.Member;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.storage.StatsField;
import dev.louismudrack.prost.storage.StatsRow;
import dev.louismudrack.prost.ui.Fmt;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * %prost_...% for TAB, scoreboards, holograms. Resolved per viewer on every refresh, so nothing here touches the
 * database on the calling thread: leaderboards come from the store's cache, totals from a 30 s per-player cache.
 */
public final class ProstExpansion extends PlaceholderExpansion implements Listener {
    private static final Pattern TOP = Pattern.compile("top_(\\d{1,2})_(name|value)");
    private static final Set<String> PLAYER_PARAMS = Set.of("in_round", "round_code", "round_players", "challenge",
            "challenge_time", "sips_session", "sips_total", "jokers", "limit_left", "tag");
    private static final long TOTAL_CACHE_MS = 30_000L;

    private static ProstExpansion registered;

    private final ProstPlugin plugin;
    private final Map<UUID, Integer> totals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> totalsAt = new ConcurrentHashMap<>();

    public ProstExpansion(ProstPlugin plugin) { this.plugin = plugin; }

    /** a static entry keeps ProstPlugin's bytecode free of PlaceholderAPI types, which the verifier would load */
    public static void registerFor(ProstPlugin plugin) {
        ProstExpansion expansion = new ProstExpansion(plugin);
        if (!expansion.register()) {
            plugin.getLogger().warning("PlaceholderAPI did not register %prost_*%: the identifier is taken (a stale expansion?)");
            return;
        }
        registered = expansion;
        plugin.getServer().getPluginManager().registerEvents(expansion, plugin);
    }

    /** persist() keeps the expansion across /papi reload, so it must go when Prost does */
    public static void unregisterFor() {
        if (registered == null) return;
        registered.unregister();
        registered = null;
    }

    @Override public String getIdentifier() { return "prost"; }

    @Override public String getAuthor() { return "Kasheyy"; }

    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }

    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Matcher top = TOP.matcher(params);
        if (top.matches()) return top(Integer.parseInt(top.group(1)), top.group(2).equals("name"));
        if (!PLAYER_PARAMS.contains(params)) return null;
        if (player == null) return "";
        UUID id = player.getUniqueId();
        Round r = plugin.rounds().of(id);
        Member m = r == null ? null : r.member(id);
        ChallengeRun run = r == null ? null : plugin.director().running(r);
        return switch (params) {
            case "in_round" -> String.valueOf(r != null);
            case "round_code" -> r == null ? "" : r.code;
            case "round_players" -> r == null ? "0" : String.valueOf(r.members.size());
            case "challenge" -> run == null ? "" : run.def.name(Msg.lang(id));
            case "challenge_time" -> run == null ? "" : Fmt.clock(run.remainingMs());
            case "sips_session" -> m == null ? "0" : String.valueOf(m.tally.drunk);
            case "sips_total" -> String.valueOf(total(id));
            case "jokers" -> m == null ? "0" : String.valueOf(m.jokers);
            case "limit_left" -> m == null ? "" : String.valueOf(SipLedger.room(m.history, System.currentTimeMillis(), m.limit));
            case "tag" -> LegacyComponentSerializer.legacySection().serialize(Msg.mm(Msg.str(id, "placeholder.tag")));
            default -> throw new IllegalStateException(params);
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        totals.remove(e.getPlayer().getUniqueId());
        totalsAt.remove(e.getPlayer().getUniqueId());
    }

    int cachedTotals() { return totals.size() + totalsAt.size(); }

    private int total(UUID id) {
        long now = System.currentTimeMillis();
        if (now - totalsAt.getOrDefault(id, 0L) >= TOTAL_CACHE_MS) {
            totalsAt.put(id, now);
            plugin.stats().row(id, row -> totals.put(id, row == null ? 0 : row.drunk()));
        }
        return totals.getOrDefault(id, 0);
    }

    private String top(int place, boolean name) {
        List<StatsRow> rows = plugin.stats().cachedTop(StatsField.DRUNK);
        if (place < 1 || place > rows.size()) return name ? "-" : "0";
        StatsRow row = rows.get(place - 1);
        return name ? row.name() : String.valueOf(row.drunk());
    }
}
