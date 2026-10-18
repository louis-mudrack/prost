package dev.louismudrack.prost.tracker;

import dev.louismudrack.prost.catalogue.StatTypes;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** vanilla statistics, snapshotted at the start: only what happens during the challenge counts (spec §5) */
final class StatTracker extends BaseTracker {
    private final List<Statistic> stats = new ArrayList<>();
    private final Set<Material> mats;
    private final Set<EntityType> ents;
    private final int scale;
    private final Map<UUID, Long> start = new HashMap<>();

    StatTracker(TrackContext ctx) {
        super(ctx);
        List<String> names = StatTypes.split(ctx.def().stat());
        for (String n : names) stats.add(Statistic.valueOf(n));
        StatTypes.Target target = StatTypes.KNOWN.get(names.get(0));
        mats = target == StatTypes.Target.MATERIAL ? materials(ctx.def().targets()) : java.util.EnumSet.noneOf(Material.class);
        ents = target == StatTypes.Target.ENTITY ? entities(ctx.def().targets()) : java.util.EnumSet.noneOf(EntityType.class);
        scale = StatTypes.scale(names.get(0));
    }

    @Override
    protected void begin(List<Player> ps) { for (Player p : ps) start.put(p.getUniqueId(), raw(p)); }

    @Override
    public void poll(List<Player> online) {
        for (Player p : online) {
            Long s = start.get(p.getUniqueId());
            if (s != null) progress.put(p.getUniqueId(), (int) ((raw(p) - s) / scale));
        }
    }

    private long raw(Player p) {
        long sum = 0;
        for (Statistic s : stats) {
            switch (s.getType()) {
                case UNTYPED -> sum += p.getStatistic(s);
                case ITEM, BLOCK -> { for (Material m : mats) sum += p.getStatistic(s, m); }
                case ENTITY -> { for (EntityType e : ents) sum += p.getStatistic(s, e); }
            }
        }
        return sum;
    }
}
