# Changelog

## 0.4.2 - 2026-10-20
### Fixes
- Build contest tour: a player is no longer left falling or hurt after the tour sends them home. They stay protected
  until they stand on something again (30 seconds at most; flying counts), and a return that is refused, fails or leads
  into a world that is not loaded is tried again every 5 seconds for a minute while they stay protected (not while they
  ride something they mounted meanwhile), then on their next join. Anything riding on a player is set down before every
  teleport, a visit that is refused is told and the tour goes on, and a player on the death screen is skipped instead of
  being told another plugin refused the visit.
- A challenge that fails to start or to stop no longer blocks the round: it is cancelled and freed at once, the others
  keep running, and the next challenge can be drawn.
- A player who has just left the server is no longer drawn into a challenge.
- Hot potato: the first holder is announced once, the end of one round no longer takes the potatoes of another round
  from players, and a marked potato lying on the ground is removed when a player touches it.
- Luck: a wheel of fortune left with one spinner says so in its result line, and a mystery box whose taker left is no
  longer shown as untouched.
- Votes: a vote cast after the clock has run out no longer counts.
- challenges.yml after an update: a `.catalogue-version` file that holds no version is reported on each start and
  `/prost reload`, and nothing is added until it is fixed or deleted, and when the new challenges cannot be appended the log now says why (the file
  would not read any more, one of your entries would change, or the number of entries would change).

## 0.4.1 - 2026-10-20
### Fixes
- Hot potato: an admin who clicks the potato in the holder's inventory through /invsee or OpenInv no longer removes it
  from the game; the click is only refused.
- The "sit" reaction no longer counts a boat or minecart seat that another plugin refuses (a protected region, say).
- Build contests respect a closed Nether or End (`dimensions` in config.yml or a plugin's gate): `/prost here` there is
  refused, no build is placed there when the building time ends, and the tour does not visit a build whose dimension is
  closed by its turn (it still gets votes; with `vote.teleport: false` its coordinates are still named).

## 0.4.0 - 2026-10-20
26 new challenges in three new kinds that play like party games: 129 challenges in eight kinds now.

### New challenges
- **Reaction** (9, two players or more; five also play solo): do it faster than the others - crouch, jump into water,
  write "Prost" in chat, look straight up, jump, hold dirt, sit down (boat, minecart, mount or a sit plugin's seat) or
  eat something. Whoever is not in time drinks; when everybody is, the last one drinks. **Freeze:** whoever moves after
  a two-second grace drinks (looking around is fine). Played solo: in time, or drink. Already sitting, holding dirt,
  standing in water or looking up when it starts does not count - do it again. A reaction nobody can do where they are
  is not drawn (water while someone is in the Nether, eating while someone's food bar is full, dirt in the Nether or End).
- **Hot potato** (a reaction, two players or more): a marked poisonous potato goes to a random player, who passes it on by
  dropping it next to another one. Whoever holds it when the hidden clock runs out (60 to 120 seconds) drinks 3. The
  potato cannot be stored, eaten, burnt or lost, a holder who dies or leaves passes it on, and every Prost potato is
  removed when the challenge ends, on start-up and when a player joins.
- **Luck** (6, in menus): wheel of fortune, heads or tails, three chests (drink, joker or hand out), a die, higher or
  lower, and mystery boxes with one trap. Everybody decides in a menu with the clock; whoever has not decided at the end
  gets a random choice.
- **Build contests** (10, kind Vote, three players or more): the ugliest house, a statue, 8x8 pixel art, an armor stand
  pose, a flower garden, a portrait of a player you are told in secret, an outfit, a sign, a tiny house and a bridge.
  Everybody builds, a tour shows each build, then everybody votes in a menu: the fewest votes drink, and whoever did not
  vote drinks one sip more. When too many players drop out, the contest is cancelled and nobody drinks.
- The tour teleports everybody to each build and back to where they stood. The way back is written to `returns.yml`
  before anyone moves, so a quit, a death, a server stop or a crash still brings them back (after a crash when they join
  again). During the tour and for 3 seconds after it nobody takes damage or can change or take anything at a build, and
  fire or frost caught there is gone on the return. `vote.teleport: false` names the builds' coordinates instead.
- One penalty per challenge holds for the new kinds too: when anybody drinks, nobody gets a reward. Rewards come only when
  nobody drinks: a freeze nobody broke, a solo reaction done in time, a luck roll that leaves nobody drinking (a solo
  wheel of 4-10, a chest's joker or hand-out, a die of 5-6, every high/low guess right). A build contest never rewards.

### Commands
- `/prost here` (German `hier`, also a button on the challenge card) marks your build where you stand; without it the
  build is where you stand when the building time ends.
- `/prost play` (a chat button) opens a luck game's or a vote's menu again after you closed it.

### Configuration
- New `game.yml` keys, appended to your file with their defaults on the first start: `kind-weights` `reaction: 7`,
  `luck: 3`, `vote: 5`; `reaction.freeze-grace-seconds: 2`, `potato.pass-seconds: 3`, `luck.reveal-seconds: 2`,
  `vote.view-seconds: 15`, `vote.vote-seconds: 20`, `vote.teleport: true`.
- Catalogue entries have a `since` field (the version that added a shipped entry); the new trackers and their fields are
  in docs/challenges.md.

### Existing servers
- The first start of 0.4.0 appends the 26 new challenges to your `challenges.yml`, once, under `# added by Prost 0.4.0`.
  Nothing above them changes: not your values, comments or line endings, and entries you deleted stay deleted. A new
  challenge whose id your file already has is skipped, so your own entry of that name stays.
- `.catalogue-version` remembers which version's challenges the file has, so a new challenge you delete later is not
  added again. A file that is not valid YAML gets nothing until you fix it (the shipped catalogue plays meanwhile, as
  before); the console says so. A catalogue taken over from AniDrinks gets the new challenges too.

## 0.3.1 - 2026-10-19
- A config.yml with a YAML error, or an empty one, now falls back to the last settings that worked
  (`.config.last-good.yml`), so a MySQL server keeps its stats in MySQL; one console error names the problem.
  `/prost reload` with a broken file keeps the running settings.
- A value of the wrong type in config.yml (one world instead of a list, a port or `true`/`false` in quotes) is read as
  meant and warns, instead of being ignored.
- `grey` and `dark_grey` work as theme colours.
- A plugin named Essentials that is not EssentialsX no longer stops Prost from starting; Prost's own AFK detection is used.
- The "who plays" choice for a Solo or Duel card now has the same time limit as every other choice; when it runs out,
  the game picks the players.
- Prost's page is now https://hangar.papermc.io/Kasheyy/Prost; downloads are on Hangar and on GitHub. Language files
  from 0.3.0 get the new link in the update notice unless you changed that text.
- The update check asks the GitHub releases and gives up connecting after 10 seconds.
- Anonymous usage statistics on bStats (switch off with `metrics: false`).
- Smaller fixes: no AFK or placeholder entry is left behind when a player leaves (`%prost_sips_total%` is `0` for players
  who are not online), saved rules read their language in any case, and the "or" between rewards and the "vs" of a
  Duel keep their spaces in every translation.

## 0.3.0 - 2026-10-19
First release as Prost (grew out of AniDrinks 0.2.0 on the GoofyCraft survival server).
- English and German, every text in lang/<code>.yml, per-player language (client language or /prost language).
- English subcommands with per-language aliases; /drinks alias; tab completion.
- Hooks API (MIT) for language, AFK, bots, Nether/End and excluded areas; events for rounds, challenges, sips, roulette.
- SQLite or MySQL/MariaDB stats (delta writes, safe for several servers), PlaceholderAPI placeholders and parsing,
  EssentialsX AFK, bStats, Modrinth update checker.
- Takes over an AniDrinks installation automatically when Prost holds no stats yet; otherwise with /prost admin migrate.
- One penalty per challenge: when anyone drinks, winners get no reward on top.
