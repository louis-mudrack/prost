package dev.louismudrack.prost.config;

import java.util.List;
import java.util.Map;

/** A block-aligned box in one world (config.yml excluded-areas); both corners inclusive, given in any order. */
public record AreaBox(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public boolean contains(String worldName, double x, double y, double z) {
        return world.equals(worldName) && x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    /** {world: name, min: [x, y, z], max: [x, y, z]} - null when anything is missing or not a number */
    static AreaBox parse(Object raw) {
        if (!(raw instanceof Map<?, ?> m) || m.get("world") == null) return null;
        int[] a = corner(m.get("min")), b = corner(m.get("max"));
        if (a == null || b == null) return null;
        return new AreaBox(String.valueOf(m.get("world")), Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]),
                Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]));
    }

    private static int[] corner(Object v) {
        if (!(v instanceof List<?> l) || l.size() != 3) return null;
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(l.get(i) instanceof Number n)) return null;
            out[i] = n.intValue();
        }
        return out;
    }
}
