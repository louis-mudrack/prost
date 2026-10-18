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

- 103 challenges in 5 kinds (Team, Each, Race, Duel, Solo), all editable in `challenges.yml`
- 12 goal trackers: mining, statistics, collecting, gifts, inventory, counting, distinct things, reaching places, avoiding
  things, wearing items, building towers and meeting up
- Rewards for winners: hand out sips, make a rule, jokers, pick the next challenge; a prepared rule list in `rules.yml`
- The action roulette: small random chances on everyday actions, capped per player
- Per-player languages (English and German shipped, any other by dropping in a file)
- Placeholders for TAB, scoreboards and holograms (PlaceholderAPI), PlaceholderAPI values inside Prost's own texts
- Stats in SQLite (nothing to set up) or MySQL/MariaDB (shared by several servers)
- A small API with hooks and events for other plugins

## Requirements

- Paper or a Paper fork, Minecraft 1.21.4 - 26.x
- Java 21 or newer
- Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), EssentialsX (its AFK state is used)

## Install and a first round

1. Put the Prost jar into `plugins/` and start the server. Prost writes its files to `plugins/Prost/`.
2. `/prost` opens the menu. `/prost start` opens a round (optionally `/prost start party`) and makes you the host.
3. `/prost invite <player>` sends an invite with an accept button; others can also type `/prost join <code>`.
4. The first challenge comes after the warm-up (2 minutes by default), then one every few minutes depending on the pace.

## Commands

`/prost` (alias `/drinks`, more in `commands.aliases`). Subcommand names are English; every language file can add
its own names (the shipped German file has e.g. `einladen`, `ablehnen`, `verlassen`, `weiter`, `tempo`, `ende`, `sprache`).
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
| `/prost admin reload` | read all files again (also `/prost reload`) | `prost.admin`, console |
| `/prost admin list` | list running rounds | `prost.admin`, console |
| `/prost admin end <code>` | end a round | `prost.admin`, console |
| `/prost admin force <code> <id> <kind>` | start a specific challenge in a round | `prost.admin`, console |
| `/prost admin debug [on\|off]` | roulette trace in the console | `prost.admin`, console |
| `/prost admin migrate` | take over an AniDrinks installation | `prost.admin`, console |

The chat buttons run `/prost reward`, `/prost handout` and `/prost joker` (reopen a reward menu, the sips you may hand out,
use a joker); they are not meant to be typed but work. All other commands are for players only; the console can use `reload` and `admin`.

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
| `game.yml` | you, new keys are appended | max players, pace, kind weights, reward time, jokers, limits, roulette, rules |
| `challenges.yml`, `rules.yml` | you, written once | the challenge and rule catalogues ([docs/challenges.md](docs/challenges.md)) |
| `lang/*.yml` | you, new keys are appended | every text ([docs/translations.md](docs/translations.md)) |
| `rounds.yml` | the plugin | running rounds, restored after a restart |
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
| `%prost_sips_total%` | all-time sips drunk (cached for 30 seconds) |
| `%prost_jokers%` | jokers held |
| `%prost_limit_left%` | sips left before the hourly limit, empty outside a round |
| `%prost_tag%` | the `[Prost]` tag in the player's language, for scoreboards and TAB |
| `%prost_top_<n>_name%` | name of place `n` (1-10) in the all-time sips drunk list, `-` if empty |
| `%prost_top_<n>_value%` | sips of place `n`, `0` if empty |

The top list is cached for 30 seconds.

## For developers

Prost has a small API (hooks and events), MIT licensed. Add it as a soft dependency in your `plugin.yml`:

```yaml
softdepend: [Prost]
```

```java
public final class MyNetwork extends JavaPlugin {
    @Override
    public void onEnable() {
        ProstApi prost = getServer().getServicesManager().load(ProstApi.class);   // null without Prost
        if (prost == null) return;
        prost.registerLanguageProvider(this, player -> Optional.ofNullable(languageOf(player.getUniqueId())));
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onSips(SipsEvent e) {
                getLogger().info(e.player() + " drank " + e.sips() + " (" + e.source() + ")");
            }
        }, this);
    }

    private String languageOf(UUID player) { return "en"; }   // your own lookup
}
```

Everything is described in [docs/api.md](docs/api.md).

## Building

```bash
./gradlew build            # unit tests and MockBukkit tests, no Docker needed
./gradlew integrationTest  # MariaDB tests through Testcontainers, needs Docker
```

The plugin jar is `Prost-<version>.jar` in `prost-plugin/build/libs/`. The toolchain is Java 21; the plugin compiles
against Paper API 1.21.4 only.

## License

The plugin is GPL-3.0 ([LICENSE](LICENSE)). The API module `prost-api` is MIT ([prost-api/LICENSE](prost-api/LICENSE)).
