package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.hook.PlaceholderSnapshots.Snapshot;
import dev.louismudrack.prost.storage.StatsField;
import dev.louismudrack.prost.storage.StatsRow;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
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
 * %prost_...% for TAB, scoreboards, holograms. Resolved per viewer on every refresh, often off the main thread, so
 * nothing here reads the rounds or the database: round values come from the main thread's per-tick copy, leaderboards
 * from the store's cache, totals from a 30 s per-player cache.
 */
public final class ProstExpansion extends PlaceholderExpansion implements Listener {
    private static final Pattern TOP = Pattern.compile("top_(\\d{1,2})_(name|value)");
    private static final Set<String> PLAYER_PARAMS = Set.of("in_round", "round_code", "round_players", "challenge",
            "challenge_time", "sips_session", "sips_total", "jokers", "limit_left", "tag");
    private static final long TOTAL_CACHE_MS = 30_000L;
    private static final Snapshot IDLE = new Snapshot(false, "", 0, "", "", 0, 0, "", "");

    private static ProstExpansion registered;

    private final ProstPlugin plugin;
    private final PlaceholderSnapshots snapshots;
    private final Map<UUID, Integer> totals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> totalsAt = new ConcurrentHashMap<>();

    public ProstExpansion(ProstPlugin plugin, PlaceholderSnapshots snapshots) {
        this.plugin = plugin;
        this.snapshots = snapshots;
    }

    /** a static entry keeps ProstPlugin's bytecode free of PlaceholderAPI types, which the verifier would load */
    public static void registerFor(ProstPlugin plugin, PlaceholderSnapshots snapshots) {
        ProstExpansion expansion = new ProstExpansion(plugin, snapshots);
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
        Snapshot s = snapshots.of(id);
        if (s == null) s = IDLE;
        return switch (params) {
            case "in_round" -> String.valueOf(s.inRound());
            case "round_code" -> s.roundCode();
            case "round_players" -> String.valueOf(s.roundPlayers());
            case "challenge" -> s.challenge();
            case "challenge_time" -> s.challengeTime();
            case "sips_session" -> String.valueOf(s.sipsSession());
            case "sips_total" -> String.valueOf(total(player));
            case "jokers" -> String.valueOf(s.jokers());
            case "limit_left" -> s.limitLeft();
            case "tag" -> s.tag();
            default -> throw new IllegalStateException(params);
        };
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        totals.remove(e.getPlayer().getUniqueId());
        totalsAt.remove(e.getPlayer().getUniqueId());
    }

    int cachedTotals() { return totals.size() + totalsAt.size(); }

    /** online players only: the quit clears their entries, so a player who is gone (or leaves before the read answers) gets none */
    private int total(OfflinePlayer player) {
        if (!player.isOnline()) return 0;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - totalsAt.getOrDefault(id, 0L) >= TOTAL_CACHE_MS) {
            totalsAt.put(id, now);
            plugin.stats().row(id, row -> { if (player.isOnline()) totals.put(id, row == null ? 0 : row.drunk()); });
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
