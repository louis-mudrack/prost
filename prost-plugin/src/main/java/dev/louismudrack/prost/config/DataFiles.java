package dev.louismudrack.prost.config;

import dev.louismudrack.prost.ProstPlugin;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The admin-owned YAML files (spec §12): the shipped copy is written once, then the server copy is read with SnakeYAML -
 * the same parser the build's CatalogueSelfTest uses. An unreadable file falls back to the jar's copy, with a warning.
 */
public final class DataFiles {
    private final ProstPlugin plugin;

    public DataFiles(ProstPlugin plugin) { this.plugin = plugin; }

    public Object load(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) plugin.saveResource(name, false);
        try (Reader r = java.nio.file.Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            return yaml().load(r);
        } catch (IOException | RuntimeException e) {   // SnakeYAML's YAMLException is a RuntimeException
            plugin.getLogger().warning(name + " is unreadable (" + e.getMessage() + ") - using the shipped copy until it is fixed");
            return shipped(name);
        }
    }

    public Object loadIfPresent(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) return null;
        try (Reader r = java.nio.file.Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            return yaml().load(r);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning(name + " is unreadable (" + e.getMessage() + ") - ignored");
            return null;
        }
    }

    public Map<?, ?> map(String name) { return load(name) instanceof Map<?, ?> m ? m : Map.of(); }

    private Object shipped(String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) return null;
            return yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("shipped " + name + " unreadable: " + e.getMessage());
            return null;
        }
    }

    private static Yaml yaml() {
        LoaderOptions o = new LoaderOptions();
        o.setAllowDuplicateKeys(false);
        return new Yaml(o);
    }
}
