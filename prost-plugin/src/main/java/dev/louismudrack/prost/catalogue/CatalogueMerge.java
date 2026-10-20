package dev.louismudrack.prost.catalogue;

import dev.louismudrack.prost.ProstPlugin;
import dev.louismudrack.prost.config.DataFiles;
import dev.louismudrack.prost.config.YamlErrors;
import dev.louismudrack.prost.meta.Versions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * challenges.yml is the admin's: an update appends the entries its version added, as text below a comment, so the
 * admin's edits, deletions, comments and line endings stay byte for byte. .catalogue-version holds the version whose
 * entries the file has received, so an entry deleted after that is not added again.
 */
public final class CatalogueMerge {
    public static final String FILE = "challenges.yml";
    public static final String MARKER = ".catalogue-version";
    /** a challenges.yml without a marker was written by 0.3.1 or earlier, whose entries carry no since */
    private static final String UNMARKED = "0.3.1";

    private static final Pattern VERSION = Pattern.compile("v?\\d+(\\.\\d+)*(-[0-9A-Za-z.-]+)?");

    /** a line of the shipped file: the entry's id, the version that added it (null: one of the first) and the line itself */
    private record Shipped(String id, String since, String line) {}

    private CatalogueMerge() {}

    /**
     * The number of entries appended to the server's challenges.yml. Call it right before {@code files.load(FILE)}: that
     * load reports an unreadable file, which is why the catch here is silent.
     */
    public static int ensure(ProstPlugin plugin) {
        String version = plugin.getPluginMeta().getVersion();
        if (!Files.exists(dataFile(plugin, FILE))) {
            plugin.saveResource(FILE, false);
            mark(plugin, version);
            return 0;
        }
        Optional<String> seen = seen(plugin);
        // up to date, or a downgrade: the newer version's marker stays for when it runs again
        if (seen.isEmpty() || !Versions.newer(version, seen.get())) return 0;
        return append(plugin, version, seen.get());
    }

    private static int append(ProstPlugin plugin, String version, String seen) {
        byte[] content;
        Object parsed;
        try {
            content = Files.readAllBytes(dataFile(plugin, FILE));
            parsed = DataFiles.parse(content);
        } catch (IOException | RuntimeException e) {   // SnakeYAML's YAMLException is a RuntimeException
            return 0;   // DataFiles.load reports it right after; the marker stays, so the fixed file gets them
        }
        if (parsed != null && !(parsed instanceof Map<?, ?>)) {
            plugin.getLogger().warning(FILE + ": the top level is not a map of challenges - the new challenges of Prost " + version
                    + " are added once it is");
            return 0;
        }
        Map<?, ?> admins = parsed == null ? Map.of() : (Map<?, ?>) parsed;
        List<Shipped> added;
        try {
            added = missing(shipped(plugin), admins, seen);
        } catch (IOException e) {
            plugin.getLogger().warning(FILE + ": the copy in Prost's jar is unreadable (" + YamlErrors.describe(e) + ") - no new challenges added");
            return 0;
        }
        if (added.isEmpty()) {
            mark(plugin, version);
            return 0;
        }
        String what = "the " + added.size() + " new challenges of Prost " + version;
        byte[] merged = withBlock(content, version, added);
        String problem = problem(admins, merged, added.size());
        if (problem != null) {
            plugin.getLogger().warning(FILE + ": " + what + " are not added - " + problem + "; they follow once the file is fixed");
            return 0;
        }
        try {
            plugin.files().writeDurably(FILE, merged);
        } catch (IOException e) {
            plugin.getLogger().warning(FILE + ": " + what + " could not be added (" + YamlErrors.describe(e) + ") - they are tried again "
                    + "on the next start");
            return 0;
        }
        plugin.getLogger().info(FILE + ": " + added.size() + " new challenges added (Prost " + version + ")");
        mark(plugin, version);
        return added.size();
    }

    /** the version whose entries challenges.yml has received; empty when the marker cannot be read */
    private static Optional<String> seen(ProstPlugin plugin) {
        Path marker = dataFile(plugin, MARKER);
        if (!Files.exists(marker)) return Optional.of(UNMARKED);
        try {
            String text = Files.readString(marker, StandardCharsets.UTF_8).trim();
            if (VERSION.matcher(text).matches()) return Optional.of(text);
            plugin.getLogger().warning(FILE + ": " + MARKER + " holds no version ('" + text + "') - no new challenges added "
                    + "until it is fixed or deleted");
            return Optional.empty();
        } catch (IOException e) {
            plugin.getLogger().warning(FILE + ": " + MARKER + " is unreadable (" + YamlErrors.describe(e) + ") - no new challenges added "
                    + "until it is fixed or deleted");
            return Optional.empty();
        }
    }

    /** the shipped entries newer than seen whose id the admin's file lacks (ids are not case-sensitive, as in Catalogue.find) */
    private static List<Shipped> missing(List<Shipped> shipped, Map<?, ?> admins, String seen) {
        Set<String> defined = admins.keySet().stream().map(id -> String.valueOf(id).toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        return shipped.stream().filter(s -> s.since() != null && Versions.newer(s.since(), seen)
                && !defined.contains(s.id().toLowerCase(Locale.ROOT))).toList();
    }

    /** every shipped entry is one line (ShippedCatalogueTest): each is appended exactly as written */
    private static List<Shipped> shipped(ProstPlugin plugin) throws IOException {
        String text;
        try (InputStream in = plugin.getResource(FILE)) {
            if (in == null) throw new IOException(FILE + " is missing");
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Yaml yaml = new Yaml();
        List<Shipped> out = new ArrayList<>();
        for (String line : text.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList()) {
            if (!(yaml.load(line) instanceof Map<?, ?> entry) || entry.size() != 1)
                throw new IllegalStateException("the shipped " + FILE + " has a line that is not one whole entry: " + line);
            Map.Entry<?, ?> only = entry.entrySet().iterator().next();
            Object since = only.getValue() instanceof Map<?, ?> fields ? fields.get("since") : null;
            out.add(new Shipped(String.valueOf(only.getKey()), since == null ? null : String.valueOf(since), line));
        }
        return out;
    }

    /** a blank line, the comment and the entries, in the file's own line ending, after a line break the file may lack */
    private static byte[] withBlock(byte[] content, String version, List<Shipped> added) {
        String text = new String(content, StandardCharsets.UTF_8);   // valid UTF-8: it parsed
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder block = new StringBuilder();
        if (!text.isEmpty() && !text.endsWith("\n")) block.append(newline);
        block.append(newline).append("# added by Prost ").append(version).append(newline);
        for (Shipped s : added) block.append(s.line()).append(newline);
        byte[] tail = block.toString().getBytes(StandardCharsets.UTF_8), out = Arrays.copyOf(content, content.length + tail.length);
        System.arraycopy(tail, 0, out, content.length, tail.length);
        return out;
    }

    private static final String UNREADABLE_HINT = "the file would not read any more (are its entries indented, or does it end with '...'?)";

    /** null when the merged file reads as the admin's entries, each unchanged, plus the added ones; else the cause */
    private static String problem(Map<?, ?> admins, byte[] merged, int added) {
        Object after;
        try {
            after = DataFiles.parse(merged);
        } catch (IOException | RuntimeException e) {
            return UNREADABLE_HINT;
        }
        if (!(after instanceof Map<?, ?> entries)) return UNREADABLE_HINT;
        for (Map.Entry<?, ?> e : admins.entrySet())
            if (!Objects.equals(entries.get(e.getKey()), e.getValue()))
                return "your entry '" + e.getKey() + "' would change (a text kept with |+ or >+?)";
        return entries.size() == admins.size() + added ? null : "the number of entries would change";
    }

    private static void mark(ProstPlugin plugin, String version) {
        try {
            plugin.files().writeDurably(MARKER, (version + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning(MARKER + " not saved (" + YamlErrors.describe(e) + ") - a new challenge you delete from " + FILE
                    + " before the next start comes back then");
        }
    }

    private static Path dataFile(ProstPlugin plugin, String name) { return plugin.getDataFolder().toPath().resolve(name); }
}
