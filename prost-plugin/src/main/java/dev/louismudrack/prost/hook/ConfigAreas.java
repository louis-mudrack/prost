package dev.louismudrack.prost.hook;

import dev.louismudrack.prost.api.AreaFilter;
import dev.louismudrack.prost.config.PluginConfig;
import org.bukkit.Location;

import java.util.function.Supplier;

/** config.yml excluded-areas as the default area filter. */
public final class ConfigAreas implements AreaFilter {
    private final Supplier<PluginConfig> config;

    public ConfigAreas(Supplier<PluginConfig> config) { this.config = config; }

    @Override
    public boolean isExcluded(Location l) {
        if (l.getWorld() == null) return false;
        String world = l.getWorld().getName();
        return config.get().excludedAreas().stream().anyMatch(b -> b.contains(world, l.getX(), l.getY(), l.getZ()));
    }
}
