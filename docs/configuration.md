# Configuration

Prost has two settings files in `plugins/Prost/`: `config.yml` (general settings) and `game.yml` (the game's tunables).
Both are yours to edit. When a new Prost version adds a key, it is appended to your file with its comment on the next
start; your values are never changed. Catalogues are covered in [challenges.md](challenges.md), texts in
[translations.md](translations.md).

`/prost reload` (or `/prost admin reload`) reads the files again. Keys marked **(restart)** are read once at start and
need a server restart. A file that is not valid YAML is ignored with a warning (the shipped copy is used until you fix it).

YAML 1.1 turns a bare `on`, `off`, `yes` or `no` into a boolean. Prost treats those as you mean them: `nether: off` is
OFF, `end: on` is ON.

## config.yml

Invalid values fall back to the default and log one warning on start and on reload.

### language

| key | default | meaning |
|---|---|---|
| `language.default` | `en` | The server language: what everyone sees unless they get their own, and the fallback before English. Two or three lowercase letters. Without a readable `lang/<code>.yml` for it, Prost warns and uses `en`. |
| `language.per-player` | `true` | Each player sees their own choice (`/prost language`) or their Minecraft client language when a file exists for it. `false`: everyone sees the server language. A language set through the API still wins. |

### theme

Colours for all language files: `<primary>text</primary>` is a gradient, `<hl>` `<ok>` `<err>` `<muted>` switch the
colour. See [translations.md](translations.md). A value is a hex colour (`#ffd94d`) or a colour name (`gray`,
`dark_gray`, `gold`, ...); an invalid colour falls back to the default with a warning.

| key | default | meaning |
|---|---|---|
| `theme.primary` | `"#ffd94d:#ff8f3f"` | Gradient, two hex colours `start:end`. |
| `theme.highlight` | `"#ffd94d"` | `<hl>`, values and emphasis. |
| `theme.ok` | `"#55e07a"` | `<ok>`, success. |
| `theme.error` | `"#ff5555"` | `<err>`, penalties and errors. |
| `theme.muted` | `dark_gray` | `<muted>`, side information. |

### commands

| key | default | meaning |
|---|---|---|
| `commands.aliases` | `[drinks]` | Extra names for `/prost` **(restart)**. Letters, digits, `_` and `-`; `prost` itself is skipped. An alias another plugin already uses is not registered (one warning). Subcommand names per language are in `lang/<code>.yml`. |

### worlds and areas

| key | default | meaning |
|---|---|---|
| `worlds` | `[]` | Worlds where rounds play. Empty = every world. A member in another world counts as away: no challenges, no roulette. |
| `excluded-areas` | `[]` | Boxes where a position never counts for "reach a height or distance" goals, e.g. a spawn island. Each entry needs `world`, `min: [x, y, z]` and `max: [x, y, z]` (both corners inclusive, any order); an incomplete entry is skipped with a warning. |

```yaml
excluded-areas:
  - {world: world, min: [-50, 150, -50], max: [50, 320, 50]}
```

### dimensions

Whether Nether and End challenges may be drawn.

| key | default | meaning |
|---|---|---|
| `dimensions.nether` | `auto` | `auto`: only while a Nether world is loaded and the server allows the Nether (`allow-nether`). `on`: always. `off`: never. |
| `dimensions.end` | `auto` | The same for the End (`allow-end` in `bukkit.yml`). |

Any other value is treated as `auto` with a warning. A plugin can take over this decision through the API.

### afk

AFK members get no challenges and no roulette.

| key | default | meaning |
|---|---|---|
| `afk.minutes` | `5` | No movement on the ground, look change, chat or command for this many minutes = AFK. `0`: never AFK. |
| `afk.use-essentials` | `true` | Use EssentialsX's AFK state instead, when EssentialsX is installed **(restart)**. A plugin can take over AFK through the API. |

### placeholderapi and notices

| key | default | meaning |
|---|---|---|
| `placeholderapi.parse-messages` | `true` | Resolve `%placeholders%` in Prost's messages for each viewer (needs PlaceholderAPI). MiniMessage markup inside a placeholder's value is shown literally; legacy colour codes (`&a`, `§a`) in a value still colour the text. Text players type (names, rules) is never parsed. |
| `notices.first-round` | `true` | The first time a player joins any round: any drink counts, know your limits. |

### storage

Stats are stored in SQLite or MySQL/MariaDB. All of `storage:` needs a restart.

| key | default | meaning |
|---|---|---|
| `storage.type` | `sqlite` | `sqlite` (the file `stats.db` in the plugin folder, nothing to set up) or `mysql` (MySQL or MariaDB, shareable between servers). Anything else: `sqlite` with a warning. |
| `storage.table-prefix` | `"prost_"` | Prefix of the table names: up to 16 letters, digits and `_`, not starting with a digit. Invalid: `prost_` with a warning. |
| `storage.mysql.host` | `localhost` | |
| `storage.mysql.port` | `3306` | 1-65535, else `3306` with a warning. |
| `storage.mysql.database` | `prost` | |
| `storage.mysql.user` | `prost` | |
| `storage.mysql.password` | `""` | Never logged. |
| `storage.mysql.parameters` | `"useSSL=false"` | Appended to the JDBC URL. |

Counters are written as increments, so several servers can share one database. A dropped connection is reopened on the
next write. If the database cannot be opened on start, the game still runs and stats are written once it is reachable.

### metrics and update-checker

| key | default | meaning |
|---|---|---|
| `metrics` | `true` | Anonymous usage statistics on bstats.org **(restart)**. Also off when bStats is switched off server-wide. Prost sends nothing until the plugin is registered on bstats.org, whatever this says. |
| `update-checker` | `true` | Ask Modrinth once at start whether a newer Prost exists and tell `prost.admin` players on join **(restart)**. A failure only logs one line. |

## game.yml

Missing or invalid values use the defaults, so a half-edited file still works. "Number" below means a whole number of
zero or more; a negative or non-numeric value is replaced by the default. Unlike `config.yml`, no warning is logged.

| key | default | meaning |
|---|---|---|
| `max-players` | `8` | Players per round. |
| `warmup-seconds` | `120` | Time from opening a round (or restoring it after a restart) to the first challenge. |
| `pace` | `chill: [720, 1080]`, `normal: [480, 720]`, `party: [240, 420]` | Seconds between two challenges: a random time between the two numbers. Both must be above 0 and the first no larger than the second, otherwise that pace uses its default. |
| `kind-weights` | `team: 20, each: 25, race: 15, duel: 10, solo: 15` | How likely each challenge kind is among the kinds that fit the number of active players. All zero: every kind is equally likely. |
| `reward-seconds` | `60` | How long winners have to choose a reward or hand out sips; after that the first listed reward is taken, and sips not yet assigned go to random members. Also added to the pause before the next challenge. |
| `joker.max` | `2` | Jokers a member can hold; a third one lapses. |
| `joker.offer-seconds` | `10` | How long the "use a joker" button stays after a penalty. |
| `limit.default` | `20` | Sips per rolling hour that a new member starts with (each member can change theirs in the menu). Clamped into min-max. |
| `limit.min` | `5` | Lowest limit a member can set. |
| `limit.max` | `40` | Highest limit a member can set. If `min` is below 1 or above `max`, min and max both reset to 5 and 40. |
| `roulette.chances` | see below | Chance (0 to 1) per action category that the roulette hits. A value outside 0-1 is ignored. |
| `roulette.max-hits` | `3` | Hits per member within the window. |
| `roulette.window-minutes` | `30` | The window for `max-hits`. |
| `roulette.min-gap-minutes` | `4` | Minimum time between two hits on the same member. |
| `roulette.weights` | `[40, 30, 20, 10]` | Relative likelihood of landing on 1, 2, 3, 4 sips; the list length is the maximum number of sips. Needs at least one entry and a sum above 0. |
| `rules.max-active` | `5` | Active rules per round; a new rule then replaces one the winner picks. |
| `rules.reminder-minutes` | `10` | How often the active rules are repeated in chat. |
| `rules.custom-max-length` | `60` | Maximum characters of a rule a winner types. |
| `offline-grace-minutes` | `10` | A member who stays offline this long is removed from the round. |

Roulette chances, per action (default): `block: 0.003` (breaking or placing a block), `craft: 0.01`, `kill: 0.02`,
`eat: 0.02`, `fish: 0.02`, `breed: 0.02`, `trade: 0.02`, `enchant: 0.02`, `advancement: 0.10`, `death: 0.10`,
`sleep: 0.10`, `boss: 0.25` (beating a boss). A category you leave out keeps its default; set it to `0` to turn it off.
`/prost admin debug on` prints every roll to the console.
