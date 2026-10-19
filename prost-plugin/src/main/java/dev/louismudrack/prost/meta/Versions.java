package dev.louismudrack.prost.meta;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Version numbers like 0.3.0, v0.4.0, 0.3.0-SNAPSHOT; a release is newer than its own snapshot or beta. */
public final class Versions {
    private Versions() {}

    public static boolean newer(String candidate, String current) { return compare(candidate, current) > 0; }

    public static boolean atLeast(String version, String minimum) { return compare(version, minimum) >= 0; }

    static int compare(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(i < x.length ? x[i] : 0, i < y.length ? y[i] : 0);
            if (c != 0) return c;
        }
        return Boolean.compare(!suffixed(a), !suffixed(b));
    }

    private static String bare(String v) { return v.trim().replaceFirst("^[vV]", ""); }

    private static boolean suffixed(String v) { return bare(v).contains("-"); }

    private static int[] numbers(String v) {
        String core = bare(v);
        int dash = core.indexOf('-');
        String[] parts = (dash < 0 ? core : core.substring(0, dash)).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = parts[i].matches("\\d+") ? Integer.parseInt(parts[i]) : 0;
        return out;
    }

    /**
     * GitHub's release list: the highest tag that is neither a draft nor a pre-release, without its v; null when there is
     * none. GitHub sorts by date, so a fix for an older line may come first. A body that is no such list throws.
     */
    public static String latestRelease(String json) {
        String latest = null;
        for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject release = e.getAsJsonObject();
            if (flag(release, "draft") || flag(release, "prerelease") || !release.has("tag_name")) continue;
            String version = bare(release.get("tag_name").getAsString());
            if (latest == null || newer(version, latest)) latest = version;
        }
        return latest;
    }

    private static boolean flag(JsonObject release, String key) { return release.has(key) && release.get(key).getAsBoolean(); }
}
