package dev.louismudrack.prost.tracker;

/**
 * Where a game menu (27 slots) puts {@code count} options, as in a choice menu: up to four spaced out in the middle row,
 * up to a row side by side, more in every slot but the clock (4) and Close (26), the middle row first.
 */
final class MenuSlots {
    private static final int MIDDLE = 13, SPACED_MAX = 4, ROW = 9;
    private static final int[] ALL = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 0, 1, 2, 3, 5, 6, 7, 8};
    /** the most options a game menu can show */
    static final int MAX = ALL.length;

    private MenuSlots() {}

    /** the slot of option {@code index} (0-based) of {@code count} */
    static int of(int index, int count) {
        if (count <= SPACED_MAX) return MIDDLE - (count - 1) + 2 * index;
        if (count <= ROW) return ROW + (ROW - count) / 2 + index;
        return ALL[index];
    }
}
