package dev.louismudrack.prost.game;

import dev.louismudrack.prost.api.ChallengeView;
import dev.louismudrack.prost.api.RoundView;
import dev.louismudrack.prost.api.StatsView;
import dev.louismudrack.prost.storage.StatsRow;

import java.util.List;
import java.util.Optional;

/** Immutable API snapshots of the game's state. */
public final class Views {
    private Views() {}

    public static RoundView round(Round r, ChallengeRun running) {
        return new RoundView(r.code, r.host, r.pace.key, r.paused, List.copyOf(r.members.keySet()),
                Optional.ofNullable(running).map(Views::challenge));
    }

    public static ChallengeView challenge(ChallengeRun run) {
        return new ChallengeView(run.def.id(), run.kind.key, run.def.sips(), run.endsAt(), run.participants());
    }

    public static StatsView stats(StatsRow r) {
        return new StatsView(r.id(), r.name(), r.drunk(), r.water(), r.given(), r.won(), r.lost(), r.roulette(), r.jokers(),
                r.rules(), r.rounds());
    }
}
