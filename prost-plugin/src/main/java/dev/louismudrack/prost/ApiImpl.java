package dev.louismudrack.prost;

import dev.louismudrack.prost.api.AfkProvider;
import dev.louismudrack.prost.api.AreaFilter;
import dev.louismudrack.prost.api.BotFilter;
import dev.louismudrack.prost.api.DimensionGate;
import dev.louismudrack.prost.api.LanguageProvider;
import dev.louismudrack.prost.api.ProstApi;
import dev.louismudrack.prost.api.RoundView;
import dev.louismudrack.prost.api.StatsView;
import dev.louismudrack.prost.game.Round;
import dev.louismudrack.prost.game.Views;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** ProstApi on top of the plugin's own objects. */
final class ApiImpl implements ProstApi {
    private final ProstPlugin plugin;

    ApiImpl(ProstPlugin plugin) { this.plugin = plugin; }

    @Override public void registerLanguageProvider(Plugin owner, LanguageProvider provider) { plugin.hooks().setLanguage(owner, provider); }
    @Override public void registerAfkProvider(Plugin owner, AfkProvider provider) { plugin.hooks().setAfk(owner, provider); }
    @Override public void registerBotFilter(Plugin owner, BotFilter filter) { plugin.hooks().setBots(owner, filter); }
    @Override public void registerDimensionGate(Plugin owner, DimensionGate gate) { plugin.hooks().setDimensions(owner, gate); }
    @Override public void registerAreaFilter(Plugin owner, AreaFilter filter) { plugin.hooks().setAreas(owner, filter); }
    @Override public void unregisterAll(Plugin owner) { plugin.hooks().unregisterAll(owner); }

    @Override
    public Optional<RoundView> roundOf(UUID player) {
        Round r = plugin.rounds().of(player);
        return r == null ? Optional.empty() : Optional.of(Views.round(r, plugin.director().running(r)));
    }

    @Override
    public Collection<RoundView> rounds() {
        return plugin.rounds().all().stream().map(r -> Views.round(r, plugin.director().running(r))).toList();
    }

    @Override
    public CompletableFuture<Optional<StatsView>> stats(UUID player) {
        CompletableFuture<Optional<StatsView>> result = new CompletableFuture<>();
        plugin.stats().row(player, row -> result.complete(Optional.ofNullable(row).map(Views::stats)));
        return result;
    }
}
