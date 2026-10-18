package dev.louismudrack.prost.game;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.i18n.Msg;
import dev.louismudrack.prost.i18n.V;
import dev.louismudrack.prost.storage.StatsField;
import dev.louismudrack.prost.ui.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Every running round (spec §3): open, join, leave, end, invites, pause states, rounds.yml. Main thread only. */
public final class Rounds {
    public record Invite(String code, UUID from, long until) {}

    private static final String FILE = "rounds.yml";
    private static final long INVITE_MS = 120_000L, MINUTE_MS = 60_000L;

    private final ProstPlugin plugin;
    private final Map<String, Round> byCode = new LinkedHashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();
    private final Random rnd = new Random();

    public Rounds(ProstPlugin plugin) { this.plugin = plugin; }

    public Collection<Round> all() { return Collections.unmodifiableCollection(byCode.values()); }

    public Round byCode(String code) { return code == null ? null : byCode.get(code.trim().toUpperCase(Locale.ROOT)); }

    public Round find(String codeOrHost) {
        Round r = byCode(codeOrHost);
        if (r != null) return r;
        for (Round x : byCode.values()) {
            Member host = x.member(x.host);
            if (host != null && host.name.equalsIgnoreCase(codeOrHost)) return x;
        }
        return null;
    }

    public Round of(UUID id) {
        for (Round r : byCode.values()) if (r.members.containsKey(id)) return r;
        return null;
    }

    public Round open(Player host, Pace pace) {
        long now = System.currentTimeMillis();
        Round r = new Round(Round.newCode(rnd, byCode.keySet()), host.getUniqueId(), pace, now);
        r.nextDrawAt = now + plugin.cfg().warmupSeconds() * 1000L;
        r.lastRuleReminder = now;
        r.add(host.getUniqueId(), host.getName(), plugin.cfg().limitDefault(), false, now);
        byCode.put(r.code, r);
        save();
        return r;
    }

    public JoinRules.Result join(Player p, Round r) {
        JoinRules.Result result = JoinRules.check(of(p.getUniqueId()) != null, r, plugin.cfg().maxPlayers());
        if (result != JoinRules.Result.OK) return result;
        r.add(p.getUniqueId(), p.getName(), plugin.cfg().limitDefault(), false, System.currentTimeMillis());
        invites.remove(p.getUniqueId());
        Msg.round(r, "round.joined", V.of("name", p.getName()));
        save();
        return result;
    }

    /** a member leaves, is kicked or timed out; the last one closes the round */
    public void leave(UUID id) {
        Round r = of(id);
        if (r == null) return;
        Member m = r.member(id);
        plugin.stats().add(id, m.name, StatsField.ROUNDS, 1);
        Player leaving = Bukkit.getPlayer(id);
        if (leaving != null) Summary.member(leaving, r, m);
        int memberCount = r.members.size();
        UUID newHost = r.remove(id);
        if (r.members.isEmpty()) {
            plugin.stats().roundEnded(r.code, r.host, r.createdAt, System.currentTimeMillis(), memberCount, r.challenges);
            close(r);
            return;
        }
        Msg.round(r, "round.left", V.of("name", m.name));
        if (newHost != null) Msg.round(r, "round.host_now", V.of("name", r.member(newHost).name));
        save();
    }

    /** the host (or staff) ends the round for everyone: summary, stats, gone */
    public void end(Round r) {
        Summary.round(r);
        for (Member m : r.members.values()) plugin.stats().add(m.id, m.name, StatsField.ROUNDS, 1);
        plugin.stats().roundEnded(r.code, r.host, r.createdAt, System.currentTimeMillis(), r.members.size(), r.challenges);
        close(r);
    }

    private void close(Round r) {
        byCode.remove(r.code);
        plugin.placed().forget(r);
        save();
    }

    public void invite(Player from, Player to, Round r) {
        invites.put(to.getUniqueId(), new Invite(r.code, from.getUniqueId(), System.currentTimeMillis() + INVITE_MS));
        Msg.tell(to, "round.invite", V.of("name", from.getName()), V.of("join", "/prost join " + r.code));
        Sounds.bell(to);
    }

    public Invite invite(UUID id) {
        Invite i = invites.get(id);
        if (i != null && i.until() < System.currentTimeMillis()) {
            invites.remove(id);
            return null;
        }
        return i;
    }

    public void decline(UUID id) { invites.remove(id); }

    /** every second: AFK, away (offline or /unplug bot), creative/spectator; removes members after the offline grace */
    public void tickPresence() {
        long now = System.currentTimeMillis(), grace = plugin.cfg().offlineGraceMinutes() * MINUTE_MS;
        for (Round r : new ArrayList<>(byCode.values())) {
            for (Member m : new ArrayList<>(r.members.values())) {
                Player p = Bukkit.getPlayer(m.id);
                boolean away = p == null || plugin.hooks().bot(p);
                if (away && !m.away) m.awaySince = now;
                m.away = away;
                m.afk = !away && plugin.hooks().afk(p);
                m.wrongMode = !away && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                        || !plugin.pluginConfig().gameWorld(p.getWorld().getName()));
                if (p != null) m.name = p.getName();
                if (JoinRules.graceOver(m.away, m.awaySince, now, grace)) leave(m.id);
            }
        }
    }

    /** synchronous: small, and it has to work while the plugin is disabling (no scheduler then) */
    public void save() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Round r : byCode.values()) list.add(RoundCodec.encode(r));
        DumperOptions o = new DumperOptions();
        o.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Path file = new File(plugin.getDataFolder(), FILE).toPath(), tmp = file.resolveSibling(FILE + ".tmp");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(tmp, new Yaml(o).dump(list), StandardCharsets.UTF_8);
            java.nio.file.Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning(FILE + " not saved: " + e.getMessage());
        }
    }

    public void load() {
        if (!(plugin.files().loadIfPresent(FILE) instanceof List<?> list)) return;
        long now = System.currentTimeMillis();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            try {
                Round r = RoundCodec.decode(m, plugin.cfg().limitDefault());
                if (r == null || r.members.isEmpty()) continue;
                r.nextDrawAt = now + plugin.cfg().warmupSeconds() * 1000L;
                r.lastRuleReminder = now;
                byCode.put(r.code, r);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(FILE + ": a round could not be restored (" + e.getMessage() + ")");
            }
        }
    }
}
