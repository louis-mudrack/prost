package dev.louismudrack.prost.game;

import dev.louismudrack.prost.catalogue.DrawRules;
import dev.louismudrack.prost.hook.Hooks;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;

/** The live world as DrawRules sees it: gate state, overworld time and weather, where the participants are. */
public final class Situation {
    private Situation() {}

    public static DrawRules.Conditions of(Hooks hooks, Collection<Player> participants) {
        World overworld = Bukkit.getWorlds().get(0);
        boolean inNether = participants.stream().anyMatch(p -> p.getWorld().getEnvironment() == World.Environment.NETHER);
        boolean inEnd = participants.stream().anyMatch(p -> p.getWorld().getEnvironment() == World.Environment.THE_END);
        return new DrawRules.Conditions(hooks.open(World.Environment.NETHER), hooks.open(World.Environment.THE_END), DirectorRules.isNight(overworld.getTime()),
                overworld.hasStorm(), overworld.isThundering(), inNether, inEnd);
    }
}
