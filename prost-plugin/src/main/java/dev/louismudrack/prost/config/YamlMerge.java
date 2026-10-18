package dev.louismudrack.prost.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Admin-owned files that grow with updates (config.yml, game.yml, lang/*.yml): the shipped copy is written once; later
 * versions only append the keys the server copy lacks, with their comments. The admin's values are never touched.
 */
public final class YamlMerge {
    private YamlMerge() {}

    /** true when the file was written or extended */
    public static boolean ensure(JavaPlugin plugin, String path) {
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
            if (!appendMissing(user, shipped)) return false;
            user.save(file);
            plugin.getLogger().info(path + ": new settings added with their defaults");
            return true;
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning(path + " could not be updated (" + e.getMessage() + ") - fix the file; defaults apply to anything missing");
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
