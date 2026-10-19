package dev.louismudrack.prost.config;

import dev.louismudrack.prost.ProstPlugin;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The admin-owned YAML files: the shipped copy is written once, then the server copy is read with SnakeYAML (duplicate
 * keys refused). An unreadable file falls back to the jar's copy, with a warning; config.yml to its last good copy.
 */
public final class DataFiles {
    public static final String CONFIG = "config.yml";
    /** config.yml as it last parsed */
    static final String LAST_GOOD_CONFIG = ".config.last-good.yml";
    private static final String NOT_A_MAP = "the top level is not a map of settings";

    private final ProstPlugin plugin;

    public DataFiles(ProstPlugin plugin) { this.plugin = plugin; }

    public Object load(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) plugin.saveResource(name, false);
        try (Reader r = java.nio.file.Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            return yaml().load(r);
        } catch (IOException | RuntimeException e) {   // SnakeYAML's YAMLException is a RuntimeException
            plugin.getLogger().warning(name + " is unreadable (" + YamlErrors.describe(e) + ") - using the shipped copy until it is fixed");
            return shipped(name);
        }
    }

    public Object loadIfPresent(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) return null;
        try (Reader r = java.nio.file.Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
            return yaml().load(r);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning(name + " is unreadable (" + YamlErrors.describe(e) + ") - ignored");
            return null;
        }
    }

    public Map<?, ?> map(String name) { return load(name) instanceof Map<?, ?> m ? m : Map.of(); }

    /**
     * config.yml. Each version that parses is kept as .config.last-good.yml, and a broken one falls back to that copy with
     * all its settings: the shipped copy's storage would move a MySQL server's stats into a local SQLite file. The shipped
     * defaults apply only without a copy. A broken file is reported to problem in one line: its error's kind and position.
     */
    public Map<?, ?> config(Consumer<String> problem) {
        File f = new File(plugin.getDataFolder(), CONFIG);
        if (!f.exists()) plugin.saveResource(CONFIG, false);
        byte[] content;
        Object parsed;
        try {
            content = Files.readAllBytes(f.toPath());
            parsed = parse(content);
        } catch (IOException | RuntimeException e) {
            return lastGood(YamlErrors.describe(e), problem);
        }
        if (parsed == null) return Map.of();   // empty: every default
        if (!(parsed instanceof Map<?, ?> settings)) return lastGood(NOT_A_MAP, problem);
        keepAsLastGood(content);
        return settings;
    }

    private Map<?, ?> lastGood(String error, Consumer<String> problem) {
        String unreadable = CONFIG + " is unreadable (" + error + ")";
        Path copy = dataFile(LAST_GOOD_CONFIG);
        if (Files.exists(copy)) {
            String copyError;
            try {
                if (parse(Files.readAllBytes(copy)) instanceof Map<?, ?> settings) {
                    problem.accept(unreadable + " - the last good settings from " + LAST_GOOD_CONFIG + " are in use until it is fixed");
                    return settings;
                }
                copyError = NOT_A_MAP;
            } catch (IOException | RuntimeException e) {
                copyError = YamlErrors.describe(e);
            }
            unreadable += ", and so is " + LAST_GOOD_CONFIG + " (" + copyError + ")";
        }
        problem.accept(unreadable + " - using the shipped defaults until it is fixed");
        return shipped(CONFIG) instanceof Map<?, ?> settings ? settings : Map.of();
    }

    /** through a temp file: a stop halfway through writing must not leave a broken copy */
    private void keepAsLastGood(byte[] content) {
        Path copy = dataFile(LAST_GOOD_CONFIG), tmp = copy.resolveSibling(LAST_GOOD_CONFIG + ".tmp");
        try {
            Files.write(tmp, content);
            Files.move(tmp, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            plugin.getLogger().warning(LAST_GOOD_CONFIG + " not saved (" + YamlErrors.describe(e) + ") - a broken " + CONFIG
                    + " would fall back to an older copy or the shipped defaults");
        }
    }

    private Path dataFile(String name) { return new File(plugin.getDataFolder(), name).toPath(); }

    /** strict UTF-8, like the readers of the other files: a wrong encoding is an error, not replacement characters */
    private static Object parse(byte[] content) throws CharacterCodingException {
        return yaml().load(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString());
    }

    private Object shipped(String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) return null;
            return yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("shipped " + name + " unreadable: " + YamlErrors.describe(e));
            return null;
        }
    }

    private static Yaml yaml() {
        LoaderOptions o = new LoaderOptions();
        o.setAllowDuplicateKeys(false);
        return new Yaml(o);
    }
}
