package dev.louismudrack.prost.game;

/** Who may join a round, and when an absent member is removed. */
public final class JoinRules {
    public enum Result { OK, ALREADY_IN_ROUND, NO_SUCH_ROUND, FULL }

    private JoinRules() {}

    public static Result check(boolean inRound, Round target, int maxPlayers) {
        if (inRound) return Result.ALREADY_IN_ROUND;
        if (target == null) return Result.NO_SUCH_ROUND;
        if (target.full(maxPlayers)) return Result.FULL;
        return Result.OK;
    }

    static boolean graceOver(boolean away, long awaySince, long now, long graceMs) { return away && now - awaySince >= graceMs; }
}
