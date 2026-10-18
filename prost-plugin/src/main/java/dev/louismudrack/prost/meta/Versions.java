package dev.louismudrack.prost.meta;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Version numbers like 0.3.0, v0.4.0, 0.3.0-SNAPSHOT; a release is newer than its own snapshot or beta. */
public final class Versions {
    private Versions() {}

    public static boolean newer(String candidate, String current) { return compare(candidate, current) > 0; }

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

    /** Modrinth lists versions newest first; null when none is a release */
    public static String latestRelease(String json) {
        for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            if ("release".equals(o.get("version_type").getAsString())) return o.get("version_number").getAsString();
        }
        return null;
    }
}
