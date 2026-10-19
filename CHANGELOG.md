# Changelog

## 0.3.1 - 2026-10-19
- A config.yml with a YAML error now falls back to the last settings that worked (`.config.last-good.yml`), so a MySQL
  server keeps its stats in MySQL; one console error names the line. `/prost reload` with a broken file keeps the
  running settings.
- A value of the wrong type in config.yml (one world instead of a list, a port or `true`/`false` in quotes) is read as
  meant and warns, instead of being ignored.
- `grey` and `dark_grey` work as theme colours.
- A plugin named Essentials that is not EssentialsX no longer stops Prost from starting; Prost's own AFK detection is used.
- The "who plays" choice for a Solo or Duel card now has the same time limit as every other choice; when it runs out,
  the game picks the players.
- Prost's page is now https://hangar.papermc.io/Kasheyy/Prost; downloads are on Hangar and on GitHub.
- The update check asks the GitHub releases and gives up connecting after 10 seconds.
- Anonymous usage statistics on bStats (switch off with `metrics: false`).
- Smaller fixes: no AFK or placeholder entry is left behind when a player leaves (`%prost_sips_total%` is `0` for players
  who are not online), saved rules read their language in any case, and the "or" between rewards keeps its spaces in
  every translation.

## 0.3.0 - 2026-10-19
First release as Prost (grew out of AniDrinks 0.2.0 on the GoofyCraft survival server).
- English and German, every text in lang/<code>.yml, per-player language (client language or /prost language).
- English subcommands with per-language aliases; /drinks alias; tab completion.
- Hooks API (MIT) for language, AFK, bots, Nether/End and excluded areas; events for rounds, challenges, sips, roulette.
- SQLite or MySQL/MariaDB stats (delta writes, safe for several servers), PlaceholderAPI placeholders and parsing,
  EssentialsX AFK, bStats, Modrinth update checker.
- Takes over an AniDrinks installation automatically when Prost holds no stats yet; otherwise with /prost admin migrate.
- One penalty per challenge: when anyone drinks, winners get no reward on top.
