package dev.louismudrack.prost;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** "sleep,damage" -> the banned actions of an avoid challenge (or an also-avoid). */
final class AvoidModes {
    private AvoidModes() {}

    static Set<String> parse(String csv) {
        return Arrays.stream(csv.split(",")).map(s -> s.trim()).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
}
