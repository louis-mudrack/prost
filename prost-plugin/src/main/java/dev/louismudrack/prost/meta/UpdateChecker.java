package dev.louismudrack.prost.meta;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/** One asynchronous look at the GitHub releases at startup; a newer release is told to the console and to admins on join. */
public final class UpdateChecker implements Listener {
    /** newest first by date; without a token GitHub lists only published releases (no drafts) */
    static final String API = "https://api.github.com/repos/louis-mudrack/prost/releases?per_page=20";
    private static final String PAGE = "https://modrinth.com/plugin/prost-game";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String ADMIN = "prost.admin";
    private static final int HTTP_OK = 200;

    private final ProstPlugin plugin;
    private volatile String available;
    private final Set<UUID> told = new HashSet<>();

    public UpdateChecker(ProstPlugin plugin) { this.plugin = plugin; }

    public void check() {
        String current = plugin.getPluginMeta().getVersion();
        HttpRequest request = HttpRequest.newBuilder(URI.create(API)).timeout(TIMEOUT)
                .header("User-Agent", "Kasheyy/Prost/" + current + " (" + PAGE + ")")
                .header("Accept", "application/vnd.github+json").GET().build();
        HttpClient http = client();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> answer(response.statusCode(), response.body(), current)).exceptionally(e -> {
            plugin.getLogger().info("update check failed: " + reason(e));
            return null;
        }).whenComplete((done, e) -> http.shutdown());   // one check per start: its threads need not stay
    }

    /** one line at most; a refusal (403: GitHub's limit per address) or an unreadable body is no reason for a stack trace */
    void answer(int status, String body, String current) {
        if (status != HTTP_OK) {
            plugin.getLogger().info("update check failed: HTTP " + status);
            return;
        }
        String latest;
        try {
            latest = Versions.latestRelease(body);
        } catch (RuntimeException e) {   // Gson's texts quote the body: only the kind is logged
            plugin.getLogger().info("update check failed: GitHub's answer is not a list of releases (" + e.getClass().getSimpleName() + ")");
            return;
        }
        if (latest != null && Versions.newer(latest, current)) {
            available = latest;
            plugin.getLogger().info("Prost " + latest + " is available: " + PAGE + " (this server runs " + current + ")");
        }
    }

    static HttpClient client() { return HttpClient.newBuilder().connectTimeout(TIMEOUT).build(); }

    /** the cause's own message, or its class name when it has none ("update check failed: null" said nothing) */
    static String reason(Throwable e) {
        Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String v = available;
        if (v != null && e.getPlayer().hasPermission(ADMIN) && told.add(e.getPlayer().getUniqueId())) Msg.tell(e.getPlayer(), "update.available", V.of("version", v));
    }
}
