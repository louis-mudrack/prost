# Changelog

## 0.3.0 - unreleased
First release as Prost (grew out of AniDrinks 0.2.0 on the GoofyCraft survival server).
- English and German, every text in lang/<code>.yml, per-player language (client language or /prost language).
- English subcommands with per-language aliases; /drinks alias; tab completion.
- Hooks API (MIT) for language, AFK, bots, Nether/End and excluded areas; events for rounds, challenges, sips, roulette.
- SQLite or MySQL/MariaDB stats (delta writes, safe for several servers), PlaceholderAPI placeholders and parsing,
  EssentialsX AFK, bStats, Modrinth update checker.
- Takes over an AniDrinks installation automatically when Prost holds no stats yet; otherwise with /prost admin migrate.
- One penalty per challenge: when anyone drinks, winners get no reward on top.
