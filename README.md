![Prost](docs/assets/prost-banner.png)

# Prost

The drinking game for Minecraft servers: rounds with friends, challenges with fixed sips, real-life rewards and a roulette.

![CI](https://github.com/louis-mudrack/prost/actions/workflows/ci.yml/badge.svg)

## What it is

Prost runs a drinking game next to your normal Minecraft play. You open a round, friends join, and every few minutes a
challenge comes up ("mine 16 iron ore", "kill a creeper without explosion damage", "all stand on the same block").
Whoever misses it drinks a fixed number of sips, winners earn real-life rewards (hand out sips, make a rule, a joker that
cancels a drink, pick the next challenge), and a roulette now and then hits you for doing ordinary things. Everything
happens in real life: nothing in the game changes, there is no in-game advantage or penalty.

Prost is a party game, and any drink counts: water, soda, or alcohol if you are of legal age. Know your limits. Every
player sets their own sips-per-hour limit (anything above it lapses) and can play in water mode, and the first time
someone joins a round they get a short notice saying so (`notices.first-round`).

## Features

- 129 challenges in 8 kinds (Team, Each, Race, Duel, Solo, Reaction, Luck, Vote), all editable in `challenges.yml`; an
  update appends the challenges it adds and leaves your edits alone
- 16 trackers: mining, statistics, collecting, gifts, inventory, counting, distinct things, reaching places, avoiding
  things, wearing items, building towers, meeting up, quick reactions, a hot potato, luck games in menus and build
  contests the round votes on
- Rewards for winners: hand out sips, make a rule, jokers, pick the next challenge; a prepared rule list in `rules.yml`
- The action roulette: small random chances on everyday actions, capped per player
- Per-player languages (English and German shipped, any other by dropping in a file)
- Placeholders for TAB, scoreboards and holograms (PlaceholderAPI), PlaceholderAPI values inside Prost's own texts
- Stats in SQLite (nothing to set up) or MySQL/MariaDB (shared by several servers)
- A small API with hooks and events for other plugins

## Requirements

- Paper or a Paper fork, Minecraft 1.21.4 - 26.x
- Java 21 or newer
- Internet access on the first start: Paper downloads the MariaDB driver Prost declares into `libraries/` (also when you
  use SQLite). Without it Prost does not load; later starts use the downloaded copy.
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), EssentialsX (its AFK state is used)

## Install and a first round

1. Download `Prost-<version>.jar` from [Hangar](https://hangar.papermc.io/Kasheyy/Prost) or the
   [GitHub releases](https://github.com/louis-mudrack/prost/releases), put it into `plugins/` and start the server.
   Prost writes its files to `plugins/Prost/`.
2. `/prost` opens the menu. `/prost start` opens a round (optionally `/prost start party`) and makes you the host.
3. `/prost invite <player>` sends an invite with an accept button; others can also type `/prost join <code>`.
4. The first challenge comes after the warm-up (2 minutes by default), then one every few minutes depending on the pace.

## Commands

`/prost` (alias `/drinks`, more in `commands.aliases`). Subcommand names are English; every language file can add
its own names (the shipped German file has e.g. `einladen`, `ablehnen`, `verlassen`, `weiter`, `tempo`, `ende`, `sprache`,
`hier`).
Both always work. "Host" means the player who opened the round.

| command | what it does | who |
|---|---|---|
| `/prost` | open the menu | `prost.play` |
| `/prost start [chill\|normal\|party]` | open a round (default pace `normal`), you become the host | `prost.play` |
| `/prost join [code or host name]` | join a round; without an argument, accept your latest invite | `prost.play` |
| `/prost invite <player>` | invite an online player to your round | `prost.play`, in a round |
| `/prost decline` | decline an invite | `prost.play` |
| `/prost leave` | leave your round (the last member leaving closes it) | `prost.play`, in a round |
| `/prost pause` | pause or resume yourself: no challenges, no roulette, no sips | `prost.play`, in a round |
| `/prost pause round` | pause or resume the whole round | host |
| `/prost status` | show your round (members, running challenge, rules) | `prost.play`, in a round |
| `/prost next` | draw the next challenge now (only between challenges) | host |
| `/prost pace <chill\|normal\|party>` | change how often challenges come | host |
| `/prost kick <player>` | remove a member from the round | host |
| `/prost end` | end the round | host |
| `/prost stats [player]` | all-time stats and the top lists | `prost.play` |
| `/prost language [code\|auto]` | show or choose your language | `prost.play` |
| `/prost here` | mark your build in a build contest where you stand (also a chat button on the card) | `prost.play`, while building |
| `/prost admin reload` | read all files again (also `/prost reload`) | `prost.admin`, console |
| `/prost admin list` | list running rounds (also what `/prost admin` does without an argument) | `prost.admin`, console |
| `/prost admin end <code>` | end a round | `prost.admin`, console |
| `/prost admin force <code> <id> <kind>` | start a specific challenge in a round | `prost.admin`, console |
| `/prost admin debug [on\|off]` | roulette trace in the console | `prost.admin`, console |
| `/prost admin migrate` | take over an AniDrinks installation | `prost.admin`, console |

The chat buttons run `/prost reward`, `/prost handout`, `/prost joker` and `/prost play` (reopen a reward menu, the sips you
may hand out, use a joker, open the menu of a luck game or a build contest's vote again after closing it); they are not
meant to be typed but work. All other commands are for players only; the console can use `reload` and `admin`.

## Permissions

| permission | default | allows |
|---|---|---|
| `prost.play` | everyone | open, join and play rounds |
| `prost.admin` | op | `/prost reload`, `/prost admin ...`, update notices on join |

## Files

Everything lives in `plugins/Prost/`. Details of every setting: [docs/configuration.md](docs/configuration.md).

| file | owner | content |
|---|---|---|
| `config.yml` | you, new keys are appended | language, theme, command aliases, worlds, excluded areas, dimensions, AFK, PlaceholderAPI, notices, storage, metrics, update checker |
| `game.yml` | you, new keys are appended | max players, pace, kind weights, reward time, jokers, limits, roulette, rules, the timings of reactions, the hot potato, luck games and build contests |
| `challenges.yml` | you, new challenges are appended | the challenge catalogue ([docs/challenges.md](docs/challenges.md)) |
| `rules.yml` | you, written once | the prepared rules ([docs/challenges.md](docs/challenges.md#rulesyml)) |
| `lang/*.yml` | you, new keys are appended | every text ([docs/translations.md](docs/translations.md)) |
| `.catalogue-version` | the plugin | the Prost version whose new challenges `challenges.yml` has received |
| `rounds.yml` | the plugin | running rounds, restored after a restart |
| `returns.yml` | the plugin | where the players of a build contest tour go back to, applied after a quit or a crash; also who is still landing after a return |
| `stats.db` | the plugin | SQLite stats when `storage.type: sqlite` |

## Languages

English (`en`) and German (`de`) ship with the plugin. A player sees the first of these that exists:

1. the language a plugin sets through the API (a language provider)
2. their own choice with `/prost language <code>`
3. their Minecraft client language
4. the server language, `language.default` in `config.yml` (`en`)

`language.per-player: false` skips 2 and 3, so everyone sees the server language. A text missing from a language file
falls back to the server language and then to English. Adding a language is one file: see
[docs/translations.md](docs/translations.md).

## Placeholders

With PlaceholderAPI installed, the expansion `prost` is registered. Values are per player.

| placeholder | value |
|---|---|
| `%prost_in_round%` | `true` or `false` |
| `%prost_round_code%` | the join code of the player's round, empty outside a round |
| `%prost_round_players%` | members in the player's round, `0` outside a round |
| `%prost_challenge%` | name of the running challenge in the player's language, empty if none |
| `%prost_challenge_time%` | time left of the running challenge, empty if none |
| `%prost_sips_session%` | sips the player drank in this round |
| `%prost_sips_total%` | all-time sips drunk (cached for 30 seconds), `0` for a player who is not online |
| `%prost_jokers%` | jokers held |
| `%prost_limit_left%` | sips left before the hourly limit, empty outside a round |
| `%prost_tag%` | the `[Prost]` tag in the player's language, for scoreboards and TAB |
| `%prost_top_<n>_name%` | name of place `n` (1-10) in the all-time sips drunk list, `-` if empty |
| `%prost_top_<n>_value%` | sips of place `n`, `0` if empty |

The top list is cached for 30 seconds.

## For developers

Prost has a small API (hooks and events), MIT licensed. Compile against `prost-api` with `compileOnly` (never shade it:
Prost's jar already contains these classes) and add Prost as a soft dependency in your `plugin.yml`:

```yaml
softdepend: [Prost]
```

Without Prost the API classes do not exist, so check that Prost is enabled before any class of yours names them:

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

Everything is described in [docs/api.md](docs/api.md), including why `stats(UUID)` must not be joined on the server
thread.

## Building

```bash
./gradlew build            # unit tests and MockBukkit tests, no Docker needed
./gradlew integrationTest  # MariaDB tests through Testcontainers, needs Docker
```

The plugin jar is `Prost-<version>.jar` in `prost-plugin/build/libs/`. The toolchain is Java 21; the plugin compiles
against Paper API 1.21.4 only.

## Releasing

For the maintainer. Once: the Hangar project `Kasheyy/Prost` has to exist before the first upload (the upload only adds
versions to it). Create a Hangar API key with the permission to create versions (hangar.papermc.io, profile menu, API
keys) and store it as the repository secret `HANGAR_API_TOKEN` (Settings, Secrets and variables, Actions).

1. Set `version` in `build.gradle.kts`, add the version to `CHANGELOG.md`, push, and wait for CI to pass.
2. Tag the commit (`v<version>`), push the tag, and publish a GitHub release from it with the changelog as its text.

The release workflow then builds the tag, attaches `Prost-<version>.jar` and `prost-api-<version>.jar` to the release,
and uploads the plugin jar to Hangar (channel Release, every Paper version Hangar lists from 1.21.4 on, the release text
as the changelog). A pre-release stays on GitHub only until it is promoted to a release; that runs the workflow too.
Without the secret the Hangar step is skipped with a notice. By
hand: `HANGAR_API_TOKEN=<key> scripts/publish-hangar.sh <version> <jar> [changelog file]`; `DRY_RUN=1` prints what
would be sent.

## License

The plugin is GPL-3.0 ([LICENSE](LICENSE)). The API module `prost-api` is MIT ([prost-api/LICENSE](prost-api/LICENSE)).
