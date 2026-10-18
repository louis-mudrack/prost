package dev.louismudrack.prost;

/** the tracker for an entry (CatalogueRules has already rejected unknown trackers at load) */
final class Trackers {
    private Trackers() {}

    static Tracker create(TrackContext ctx) {
        ChallengeDef d = ctx.def();
        Tracker goal = switch (d.tracker()) {
            case "mine" -> new MineTracker(ctx);
            case "stat" -> new StatTracker(ctx);
            case "collect" -> new CollectTracker(ctx, false);
            case "gift" -> new CollectTracker(ctx, true);
            case "inventory" -> new InventoryTracker(ctx);
            case "count" -> new CountTracker(ctx);
            case "distinct" -> new DistinctTracker(ctx);
            case "reach" -> new ReachTracker(ctx);
            case "avoid" -> new AvoidTracker(ctx, AvoidModes.parse(d.mode()));
            case "wear" -> new WearTracker(ctx);
            case "tower" -> new TowerTracker(ctx);
            case "proximity" -> new ProximityTracker(ctx);
            default -> throw new IllegalArgumentException("unknown tracker " + d.tracker());
        };
        if (d.alsoAvoid().isEmpty()) return goal;
        return new CompositeTracker(goal, new AvoidTracker(ctx, java.util.Set.copyOf(d.alsoAvoid())));
    }
}
