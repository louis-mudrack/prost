package dev.louismudrack.prost.catalogue;

import java.util.Locale;

/** What has to be true when a challenge is drawn (spec §4.4). */
public enum Need {
    NETHER("nether"), END("end"), NIGHT("night"), DAY("day"), RAIN("rain"), THUNDER("thunder");

    final String key;

    Need(String key) { this.key = key; }

    static Need of(String key) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        for (Need n : values()) if (n.key.equals(k)) return n;
        return null;
    }
}
