package dev.louismudrack.prost.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** English subcommand names plus each language file's aliases (commands.<name>: [...]); aliases work for everyone. */
public final class Subcommands {
    public static final List<String> PLAYER = List.of("start", "join", "invite", "decline", "leave", "pause", "status", "next",
            "pace", "kick", "end", "stats", "language", "here");
    /** chat buttons and the argument of pause */
    public static final List<String> HIDDEN = List.of("reward", "handout", "joker", "play", "round");
    public static final List<String> ADMIN = List.of("admin", "reload");
    private static final Set<String> ALL = Stream.of(PLAYER, HIDDEN, ADMIN).flatMap(List::stream).collect(Collectors.toUnmodifiableSet());

    private final Map<String, String> toCanonical;
    private final Map<String, Map<String, List<String>>> byLanguage;

    private Subcommands(Map<String, String> toCanonical, Map<String, Map<String, List<String>>> byLanguage) {
        this.toCanonical = toCanonical;
        this.byLanguage = byLanguage;
    }

    /** on a clash the language that comes first in aliasesByLanguage keeps the name */
    public static Subcommands of(Map<String, Map<String, List<String>>> aliasesByLanguage, Consumer<String> warn) {
        Map<String, String> map = new HashMap<>();
        for (String c : ALL) map.put(c, c);
        Map<String, Map<String, List<String>>> kept = new HashMap<>();
        aliasesByLanguage.forEach((lang, aliases) -> aliases.forEach((canonical, typed) -> {
            if (!ALL.contains(canonical)) {
                warn.accept("lang/" + lang + ".yml commands." + canonical + ": there is no such subcommand");
                return;
            }
            List<String> names = kept.computeIfAbsent(lang, k -> new HashMap<>()).computeIfAbsent(canonical, k -> new ArrayList<>());
            for (String n : typed) {
                String alias = n.trim().toLowerCase(Locale.ROOT);
                if (alias.isEmpty() || alias.chars().anyMatch(Character::isWhitespace)) {
                    warn.accept("lang/" + lang + ".yml commands." + canonical + ": '" + n + "' is not a single word - skipped");
                    continue;
                }
                if (alias.equals(canonical) || names.contains(alias)) continue;
                String taken = map.get(alias);
                if (taken != null && !taken.equals(canonical)) {
                    warn.accept("lang/" + lang + ".yml: '" + alias + "' already means " + taken + " - skipped as a name for " + canonical);
                    continue;
                }
                map.put(alias, canonical);
                names.add(alias);
            }
        }));
        return new Subcommands(Map.copyOf(map), kept);
    }

    public String canonical(String typed) {
        String t = typed.trim().toLowerCase(Locale.ROOT);
        return toCanonical.getOrDefault(t, t);
    }

    /** for tab completion: the English names plus the player's language, the admin names only for admins */
    public List<String> visible(String lang, boolean admin) {
        List<String> out = new ArrayList<>();
        for (String c : PLAYER) out.addAll(namesOf(c, lang));
        if (admin) for (String c : ADMIN) out.addAll(namesOf(c, lang));
        return out;
    }

    public List<String> namesOf(String canonical, String lang) {
        List<String> out = new ArrayList<>(List.of(canonical));
        out.addAll(byLanguage.getOrDefault(lang, Map.of()).getOrDefault(canonical, List.of()));
        return out;
    }
}
