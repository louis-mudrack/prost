package dev.louismudrack.prost.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Admin-owned files that grow with updates (config.yml, game.yml, lang/*.yml): the shipped copy is written once; later
 * versions only append the keys the server copy lacks, with their comments. The admin's values are never touched - except
 * a text that still is exactly what an older version shipped, when this version ships a new one (formerDefaults).
 */
public final class YamlMerge {
    private YamlMerge() {}

    /** a file that exists and does not parse: its reader reports it, so ensure() is skipped instead of warning twice */
    public static boolean unparseable(File f) {
        if (!f.exists()) return false;
        try {
            new YamlConfiguration().load(f);
            return false;
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {   // an unknown tag comes through bare
            return true;
        }
    }

    /** true when the file was written or extended */
    public static boolean ensure(JavaPlugin plugin, String path) { return ensure(plugin, path, Map.of()); }

    /** formerDefaults: per key, the SHA-256 of texts older versions shipped there; a value still equal to one is renewed */
    public static boolean ensure(JavaPlugin plugin, String path, Map<String, Set<String>> formerDefaults) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
            return true;
        }
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return false;
            YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration user = new YamlConfiguration();
            user.load(file);
            boolean appended = appendMissing(user, shipped);
            List<String> renewed = renewFormerDefaults(user, shipped, formerDefaults);
            if (!appended && renewed.isEmpty()) return false;
            user.save(file);
            if (appended) plugin.getLogger().info(path + ": new settings added with their defaults");
            if (!renewed.isEmpty()) plugin.getLogger().info(path + ": " + String.join(", ", renewed)
                    + " still had an older version's text - now this version's");
            return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {   // Bukkit lets an unknown tag through bare
            plugin.getLogger().warning(path + " could not be updated (" + YamlErrors.describe(e) + ") - fix the file; defaults apply to anything missing");
            return false;
        }
    }

    static boolean appendMissing(YamlConfiguration user, YamlConfiguration shipped) {
        boolean changed = false;
        for (String key : shipped.getKeys(true)) {
            if (shipped.isConfigurationSection(key) || user.contains(key, true) || blockedByValue(user, key)) continue;
            String newSection = firstMissingParent(user, key);
            user.set(key, shipped.get(key));
            user.setComments(key, shipped.getComments(key));
            if (newSection != null) user.setComments(newSection, shipped.getComments(newSection));
            changed = true;
        }
        return changed;
    }

    /** the keys whose value was replaced by the shipped one */
    static List<String> renewFormerDefaults(YamlConfiguration user, YamlConfiguration shipped, Map<String, Set<String>> former) {
        List<String> renewed = new ArrayList<>();
        for (Map.Entry<String, Set<String>> e : new TreeMap<>(former).entrySet()) {
            String key = e.getKey();
            if (!user.isString(key) || !shipped.isString(key) || !e.getValue().contains(sha256(user.getString(key)))) continue;
            user.set(key, shipped.getString(key));
            renewed.add(key);
        }
        return renewed;
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {   // every Java has SHA-256
            throw new IllegalStateException(e);
        }
    }

    /** the admin turned a section into a plain value: leave their file alone below it */
    private static boolean blockedByValue(YamlConfiguration user, String key) {
        for (int dot = key.lastIndexOf('.'); dot > 0; dot = key.lastIndexOf('.', dot - 1)) {
            String parent = key.substring(0, dot);
            if (user.contains(parent, true) && !user.isConfigurationSection(parent)) return true;
        }
        return false;
    }

    /** the outermost parent section the user file does not have yet (its comment is copied too) */
    private static String firstMissingParent(YamlConfiguration user, String key) {
        String missing = null;
        for (int dot = key.lastIndexOf('.'); dot > 0; dot = key.lastIndexOf('.', dot - 1)) {
            String parent = key.substring(0, dot);
            if (!user.contains(parent, true)) missing = parent;
        }
        return missing;
    }
}
