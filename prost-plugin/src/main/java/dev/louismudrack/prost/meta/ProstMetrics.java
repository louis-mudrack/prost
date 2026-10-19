package dev.louismudrack.prost.meta;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.game.Round;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.util.Collection;

/** Anonymous usage statistics on bstats.org (config.yml metrics; bStats' own server-wide switch applies too). */
public final class ProstMetrics {
    /** the plugin's id on bstats.org, registered there as "Prost" */
    static final int BSTATS_ID = 34824;

    private ProstMetrics() {}

    /** a bStats failure costs one warning, never Prost's start */
    public static void start(ProstPlugin plugin) {
        try {
            // bStats-bukkit collects chart data on the server thread (Bukkit scheduler), so live reads are safe
            Metrics metrics = new Metrics(plugin, BSTATS_ID);
            metrics.addCustomChart(new SimplePie("storage_type", () -> plugin.pluginConfig().storage().type()));
            metrics.addCustomChart(new SimplePie("default_language", () -> plugin.lang().defaultLanguage()));
            metrics.addCustomChart(new SimplePie("per_player_language", () -> String.valueOf(plugin.pluginConfig().perPlayerLanguage())));
            metrics.addCustomChart(new SimplePie("players_per_round", () -> bucket(plugin.rounds().all())));
        } catch (RuntimeException | LinkageError e) {
            plugin.getLogger().warning("bStats did not start (" + e + ") - Prost runs without usage statistics");
        }
    }

    static String bucket(Collection<Round> rounds) { return bucketOf(rounds.stream().map(r -> r.members.size()).toList()); }

    static String bucketOf(Collection<Integer> sizes) {
        if (sizes.isEmpty()) return "no round";
        double average = sizes.stream().mapToInt(Integer::intValue).average().orElse(0);
        if (average < 2) return "1";
        if (average < 4) return "2-3";
        if (average < 6) return "4-5";
        return "6+";
    }
}
