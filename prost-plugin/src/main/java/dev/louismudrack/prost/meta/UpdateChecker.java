package dev.louismudrack.prost.meta;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** One asynchronous look at Modrinth at startup; a newer release is told to the console and to admins on join. */
public final class UpdateChecker implements Listener {
    private static final String API = "https://api.modrinth.com/v2/project/prost/version";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ADMIN = "prost.admin";

    private final ProstPlugin plugin;
    private volatile String available;

    public UpdateChecker(ProstPlugin plugin) { this.plugin = plugin; }

    public void check() {
        String current = plugin.getPluginMeta().getVersion();
        String query = "?loaders=" + enc("[\"paper\"]") + "&game_versions=" + enc("[\"" + Bukkit.getMinecraftVersion() + "\"]");
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + query)).timeout(TIMEOUT)
                .header("User-Agent", "Kasheyy/Prost/" + current + " (https://modrinth.com/plugin/prost)").GET().build();
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) {
                plugin.getLogger().fine("update check: HTTP " + response.statusCode());
                return;
            }
            String latest = Versions.latestRelease(response.body());
            if (latest != null && Versions.newer(latest, current)) {
                available = latest;
                plugin.getLogger().info("Prost " + latest + " is available on Modrinth (this server runs " + current + ")");
            }
        }).exceptionally(e -> {
            plugin.getLogger().fine("update check failed: " + e.getMessage());
            return null;
        });
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String v = available;
        if (v != null && e.getPlayer().hasPermission(ADMIN)) Msg.tell(e.getPlayer(), "update.available", V.of("version", v));
    }
}
