package dev.louismudrack.prost.meta;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** One asynchronous look at Modrinth at startup; a newer release is told to the console and to admins on join. */
public final class UpdateChecker implements Listener {
    /** by project id, not slug: the id survives a rename, and the slug "prost" belongs to another project */
    static final String API = "https://api.modrinth.com/v2/project/q9F9i8rt/version";
    private static final String PAGE = "https://modrinth.com/plugin/prost-game";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ADMIN = "prost.admin";
    private static final int HTTP_NOT_FOUND = 404;

    private final ProstPlugin plugin;
    private volatile String available;
    private final Set<UUID> told = new HashSet<>();

    public UpdateChecker(ProstPlugin plugin) { this.plugin = plugin; }

    public void check() {
        String current = plugin.getPluginMeta().getVersion();
        String query = "?loaders=" + enc("[\"paper\"]");
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + query)).timeout(TIMEOUT)
                .header("User-Agent", "Kasheyy/Prost/" + current + " (" + PAGE + ")").GET().build();
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() != 200) {
                plugin.getLogger().log(failureLevel(response.statusCode()), "update check failed: HTTP " + response.statusCode());
                return;
            }
            String latest = Versions.latestRelease(response.body());
            if (latest != null && Versions.newer(latest, current)) {
                available = latest;
                plugin.getLogger().info("Prost " + latest + " is available: " + PAGE + " (this server runs " + current + ")");
            }
        }).exceptionally(e -> {
            plugin.getLogger().info("update check failed: " + e.getMessage());
            return null;
        });
    }

    /** a draft project answers 404 until Modrinth approves it: not worth a line in the console */
    static Level failureLevel(int status) { return status == HTTP_NOT_FOUND ? Level.FINE : Level.INFO; }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String v = available;
        if (v != null && e.getPlayer().hasPermission(ADMIN) && told.add(e.getPlayer().getUniqueId())) Msg.tell(e.getPlayer(), "update.available", V.of("version", v));
    }
}
