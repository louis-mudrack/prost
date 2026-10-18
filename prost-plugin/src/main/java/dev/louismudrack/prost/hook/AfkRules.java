package dev.louismudrack.prost.hook;

/** When a player counts as active, and when idle long enough to be AFK. */
public final class AfkRules {
    private static final float LOOK_EPSILON = 0.01f;
    private static final double MOVE_EPSILON_SQ = 1.0e-4;
    private static final long MINUTE_MS = 60_000L;

    private AfkRules() {}

    /** any look change counts; a move only on the ground and outside vehicles (streams and minecarts move idle players) */
    public static boolean activity(double dx, double dz, float dyaw, float dpitch, boolean onGround, boolean inVehicle) {
        if (Math.abs(dyaw) > LOOK_EPSILON || Math.abs(dpitch) > LOOK_EPSILON) return true;
        if (inVehicle || !onGround) return false;
        return dx * dx + dz * dz > MOVE_EPSILON_SQ;
    }

    /** minutes <= 0 switches AFK detection off */
    public static boolean isAfk(long lastActiveMs, long nowMs, int minutes) {
        return minutes > 0 && nowMs - lastActiveMs >= minutes * MINUTE_MS;
    }
}
