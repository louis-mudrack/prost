# API

Prost has a small API for other plugins: five hooks that replace one of Prost's defaults, six events, and read access to
rounds and stats. The API module `prost-api` is MIT licensed; the plugin itself is GPL-3.0. The plugin jar contains the
API classes, so you compile against `prost-api` and run against Prost.

## Getting the API

1. Compile against `prost-api` (the `prost-api-<version>.jar` of a
   [GitHub release](https://github.com/louis-mudrack/prost/releases)) with `compileOnly` in Gradle or the
   `provided` scope in Maven. **Never shade or relocate it.** Prost's jar already contains these classes; a shaded copy
   is a different class, so the services manager finds no `ProstApi` even when Prost is installed.
2. Add Prost as a soft dependency so it enables first:

   ```yaml
   softdepend: [Prost]
   ```

3. Check `isPluginEnabled("Prost")` before your code touches any API class. Without Prost these classes do not exist:
   the first line that names `ProstApi` or a Prost event throws `NoClassDefFoundError`. Keep that code in a class of its
   own that is only used after the check. If your plugin cannot start without Prost use `depend` instead.

A complete example (a language provider and a `SipsEvent` listener):

```java
public final class MyNetwork extends JavaPlugin {
    @Override
    public void onEnable() {
        // nothing of Prost's is loaded before this check
        if (getServer().getPluginManager().isPluginEnabled("Prost")) ProstHook.register(this);
    }
}

/** the only class that names Prost's API */
final class ProstHook {
    static void register(JavaPlugin plugin) {
        ProstApi prost = plugin.getServer().getServicesManager().load(ProstApi.class);
        if (prost == null) return;
        prost.registerLanguageProvider(plugin, player -> Optional.ofNullable(languageOf(player.getUniqueId())));
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onSips(SipsEvent e) {
                plugin.getLogger().info(e.player() + " drank " + e.sips() + " (" + e.source() + ")");
            }
        }, plugin);
    }

    private static String languageOf(UUID player) { return "en"; }   // your own lookup
}
```

## Hooks

A hook lets your plugin decide something Prost otherwise decides by itself. Rules for all five:

- There is **one provider per hook**. A new registration replaces the previous one.
- Registrations belong to your plugin (`owner`). When it disables, or you call `unregisterAll(plugin)`, its hooks are
  removed and Prost's default answers again. A provider you replaced earlier does not come back.
- A `null` owner or hook throws `NullPointerException`.
- A provider that throws (a `LinkageError` because a dependency of yours is missing counts too) or returns `null` is
  logged once, and Prost's default answers for that call. A broken integration never stops a round.
- Register and call from the server's main thread.

| method | question Prost asks | Prost's default |
|---|---|---|
| `registerLanguageProvider(owner, LanguageProvider)` | `Optional<String> language(Player)`: the language code for this player | Prost decides: the player's `/prost language` choice, the client language, then `language.default` |
| `registerAfkProvider(owner, AfkProvider)` | `boolean isAfk(Player)`: AFK members get no challenges and no roulette | EssentialsX's AFK state when installed (`afk.use-essentials`), otherwise Prost's own detection (`afk.minutes`) |
| `registerBotFilter(owner, BotFilter)` | `boolean isBot(Player)`: bots never play, cannot be invited and count as away | players carrying the `NPC` metadata |
| `registerDimensionGate(owner, DimensionGate)` | `boolean isOpen(World.Environment)`: may Nether or End challenges be drawn (asked for `NETHER` and `THE_END`; the overworld is always open) | `dimensions.nether` / `dimensions.end` in `config.yml` |
| `registerAreaFilter(owner, AreaFilter)` | `boolean isExcluded(Location)`: positions that never count for reach goals | `excluded-areas` in `config.yml` |

The language provider has one more effect: while one is registered, `/prost language` only tells players that the
language is managed by the server. A code it returns is used when Prost has a file for it (`en`, `de`, your own). A region
is dropped (`DE-at` and `de_AT` mean `de`); only an empty result or a language Prost has no file for falls back to
Prost's own choice.

```java
prost.registerAfkProvider(this, player -> myAfkPlugin.isAfk(player.getUniqueId()));
prost.registerBotFilter(this, player -> player.hasMetadata("my-bot"));
prost.registerDimensionGate(this, env -> env != World.Environment.THE_END);   // no End challenges
prost.registerAreaFilter(this, location -> spawnRegion.contains(location));
```

## Events

All events are Bukkit events, fire on the **main server thread**, and are not cancellable. They live in
`dev.louismudrack.prost.api.event`. Views (`RoundView`, `ChallengeView`) are immutable snapshots.

| event | fires | fields |
|---|---|---|
| `RoundStartEvent` | a round was opened | `round()` |
| `RoundEndEvent` | a round closed, however it ended (host, `/prost admin end`, the last member leaving or being removed). A server stop does not fire it: rounds are saved and restored. | `round()`, its last state |
| `ChallengeStartEvent` | a challenge was announced and started | `round()`, `challenge()` |
| `ChallengeEndEvent` | a challenge ended | `round()`, `challenge()`, `resolved()` (false when it was cancelled: nobody drinks, nobody wins), `winners()`, `drinks()` (member to sips, before jokers and the hourly limit) |
| `SipsEvent` | sips actually reached a player, after jokers and the limit | `player()`, `roundCode()`, `sips()`, `source()` (`CHALLENGE`, `ROULETTE` or `HANDOUT`), `water()` |
| `RouletteHitEvent` | the roulette hit a player and landed on sips; they then arrive as a `SipsEvent` with source `ROULETTE` | `player()`, `roundCode()`, `sips()` |

A round that ends while a challenge is running fires only `RoundEndEvent` (its view still shows the running challenge); no
`ChallengeEndEvent` follows. A challenge that fails while it starts is cancelled with a `ChallengeEndEvent` (`resolved()`
false) that may come without a `ChallengeStartEvent` before it.

## Views

`roundOf(UUID)` returns the player's `Optional<RoundView>`, `rounds()` all running rounds.

- `RoundView`: `code`, `host`, `pace` (`chill`, `normal`, `party`), `paused`, `members` (UUIDs in join order),
  `challenge` (`Optional<ChallengeView>`).
- `ChallengeView`: `id` (the key in `challenges.yml`), `kind` (`team`, `each`, `race`, `duel`, `solo`, `reaction`, `luck`, `vote`), `sips`
  (what the losers drink), `endsAtMillis` (epoch milliseconds; it can move while a game runs: a build contest reports
  the end of its current phase - building, the build on show, the vote - and a luck game the end of its reveal once
  everybody has decided; a hot potato reports its real end, although players see `??`), `participants`.
- `StatsView` from `stats(UUID)`: `player`, `name` (last known), `sipsDrunk`, `sipsWater`, `sipsGiven`, `won`, `lost`,
  `rouletteHits`, `jokersUsed`, `rulesMade`, `rounds`.

## Threading

Call everything from the main server thread, with one exception: `stats(UUID)` is safe from any thread. It returns a
`CompletableFuture<Optional<StatsView>>` that completes on a background thread, never the server thread. The optional is
empty when the player never played; the future completes exceptionally when the stats cannot be read.

Do not `join()` or `get()` the future on the server thread. While the database is unreachable a read can wait for the
driver's connect timeout, long enough for the server's watchdog. Use `thenAccept` and switch back to the main thread
with the scheduler before touching the Bukkit API:

```java
prost.stats(player.getUniqueId()).thenAccept(stats -> plugin.getServer().getScheduler().runTask(plugin, () ->
        player.sendMessage("Sips drunk: " + stats.map(StatsView::sipsDrunk).orElse(0))));
```
