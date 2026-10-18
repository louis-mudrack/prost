package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.api.DimensionGate;
import dev.louismudrack.prost.config.PluginConfig;
import dev.louismudrack.prost.config.PluginConfig.DimensionMode;
import org.bukkit.World;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Nether/End challenges without a provider: config.yml dimensions.* (auto = a loaded world of that kind and enabled). */
public final class DimensionDefaults implements DimensionGate {
    private final Supplier<PluginConfig> config;
    private final Predicate<World.Environment> worldLoaded;
    private final BooleanSupplier allowNether, allowEnd;

    public DimensionDefaults(Supplier<PluginConfig> config, Predicate<World.Environment> worldLoaded, BooleanSupplier allowNether,
                             BooleanSupplier allowEnd) {
        this.config = config;
        this.worldLoaded = worldLoaded;
        this.allowNether = allowNether;
        this.allowEnd = allowEnd;
    }

    @Override
    public boolean isOpen(World.Environment env) {
        return switch (env) {
            case NETHER -> decide(config.get().nether(), worldLoaded.test(env), allowNether.getAsBoolean());
            case THE_END -> decide(config.get().end(), worldLoaded.test(env), allowEnd.getAsBoolean());
            default -> true;
        };
    }

    static boolean decide(DimensionMode mode, boolean loaded, boolean allowed) {
        return mode == DimensionMode.ON || (mode == DimensionMode.AUTO && loaded && allowed);
    }
}
