# Challenges and rules

`challenges.yml` is the challenge catalogue and `rules.yml` the list of prepared rules. Prost writes both once (129
challenges, 30 rules); after that they are yours: edit, delete and add as you like. An update never changes what is in
them; a version that adds challenges appends them to the end of `challenges.yml` once (see
[New challenges on an update](#new-challenges-on-an-update)). `/prost reload` reads them again.

A broken entry is skipped with a warning in the console and costs only that entry. If the whole file is not valid
YAML, the shipped copy is used until you fix it.

## How a challenge plays

Every few minutes (see `pace` in [configuration.md](configuration.md)) Prost draws a challenge that is possible right now
and a *kind* that fits the number of active players (not paused, not AFK, not away). Nothing repeats until all
challenges have been drawn once.

| kind | players needed | who wins, who drinks |
|---|---|---|
| `solo` | 1 | One member (the one who played the fewest solos) must reach the goal; if not, they drink. |
| `each` | 2 | Everyone has the goal; everyone who misses it drinks. |
| `team` | 2 | The goal is `amount` per head: the team succeeds when the sum of everybody's progress reaches `amount` times the number of players. Otherwise everyone drinks. For `avoid` challenges the team fails when anybody breaks the rule. |
| `race` | 2 | The first to reach the goal wins; the players with the least progress drink. |
| `duel` | 2 | Two members (the ones with the fewest duels). The first to reach the goal wins, otherwise the one with more progress; the loser drinks, a tie means both drink. |
| `reaction` | 2 | Everyone does the action as fast as they can. Whoever has not done it when the time runs out drinks; when everybody has, the last one drinks (an exact tie: all of them; when several were only found at the same check - being in water, looking up, holding the item - one of them, at random). It ends as soon as everybody has done it. `freeze`: whoever moves drinks; when nobody moves, everybody wins. Hot potato (`potato` tracker): whoever holds the potato when the hidden time runs out drinks, the others win. Only with the `reaction` and `potato` trackers. |
| `luck` | 2 | A game of chance in a menu (`luck` tracker): everyone decides, then the result is revealed. Who drinks is the game's own rule (see the `luck` tracker below). Only with the `luck` tracker. |
| `vote` | 3 | A build contest (`vote` tracker): everyone builds, the builds are shown one after the other, then everyone votes in a menu for another player's build. The fewest votes drink (a tie: all of them), and whoever did not vote drinks 1 sip more. Fewer than 3 players left while building, or fewer than 2 afterwards: cancelled, nobody drinks. Only with the `vote` tracker. |

**One penalty per challenge:** when anybody drinks, the winners get no reward on top. Rewards are only given when
nobody drinks. This is not configurable. What it means for the kinds where somebody usually drinks:

- Race, Duel and Reaction: somebody always loses, so there is normally no reward. A freeze nobody broke rewards
  everybody, and when the others left mid-challenge and one participant remains, that lone winner is rewarded.
- Luck: a reward only comes from a roll that leaves nobody drinking: a solo wheel of 4-10, a chest's joker or hand-out,
  a die of 5-6, or every high/low guess right.
- Vote: never a reward, the fewest votes always drink.

A luck game whose roll decides the reward (a chest, the die) gives exactly that reward, without a menu.

When a reward is due, each winner (for a Team: the player with the most progress) gets its menu. A winner who does not
choose in time (`reward-seconds`) gets the first option listed in `rewards`. An entry with one reward gives it without asking. What happens when a
follow-up choice is left open, each with its own `reward-seconds` window:

- **handout:** the sips already assigned go out, the rest to random members who can receive them.
- **rule:** the rule picker opens after the reward choice. On time-out a random rule from `rules.yml` is made (none when
  `rules.yml` is empty). "Own rule" with nothing typed in time makes no rule. When the round already has
  `rules.max-active` rules, a second picker asks which active rule is replaced; it has no time limit.
- **next:** a random one of the offered challenges is queued (with its players picked automatically). A Solo or Duel
  card then asks who plays; when that runs out, the card stays queued and its players are picked automatically.

## The entry format

The entry's key is its id (used by `/prost admin force <code> <id> <kind>`, not case-sensitive). Entries can be written
as one line (flow style, like the shipped file) or in block style.

```yaml
creeper_clean:
  name: {en: "Clean creeper", de: "Sauberer Creeper"}
  text: {en: "Kill {amount} creeper without taking explosion damage", de: "Töte {amount} Creeper, ohne Explosionsschaden zu nehmen"}
  tracker: stat
  stat: KILL_ENTITY
  targets: [CREEPER]
  amount: 1
  seconds: 480
  sips: 4
  give: 2
  kinds: [each, race, solo]
  rewards: [handout, joker]
  needs: [night]
  also-avoid: [explosion-damage]
  min-players: 1
  weight: 2
```

| field | required | meaning |
|---|---|---|
| `name` | yes | The short title, per language: `{en: "...", de: "..."}`. Keys are language codes. Plain text; markup is shown literally. |
| `text` | yes | What the players have to do, per language. `{amount}` is replaced by `amount`. Plain text. |
| `tracker` | yes | How progress is counted, one of the 16 below. |
| `targets` | per tracker | The blocks, items or mobs that count, as Bukkit names (`IRON_ORE`, `ZOMBIE`), or `#tags` (below). Names are checked against the running server; an unknown name skips the entry. |
| `stat` | `stat` tracker | One or more vanilla statistics, comma separated: `"WALK_ONE_CM, SPRINT_ONE_CM"` adds them up. |
| `source` | `distinct` tracker | What is counted: `mine`, `collect`, `consume`, `craft`, `kill` or `biome`. |
| `mode` | some trackers | Sub-type of the tracker, see the table. |
| `value` | some trackers | A number or name, depending on the tracker. |
| `amount` | no, default 1 | The goal, in the tracker's unit. Per player; for Team multiplied by the number of players. |
| `seconds` | yes | Time limit, 10 to 900. |
| `sips` | yes | The fixed penalty, 1 to 6. Shown at the start. |
| `give` | no, default `sips` | Sips a winner may hand out with the `handout` reward. |
| `kinds` | yes | Which kinds this challenge may be played as: `team`, `each`, `race`, `duel`, `solo`, `reaction`, `luck`, `vote`. |
| `rewards` | yes | What winners can choose from, see below. |
| `needs` | no | Conditions for drawing it, see below. |
| `also-avoid` | no | Actions that break the challenge even if the goal is reached (for example kill a creeper without explosion damage). Same names as the `avoid` modes. |
| `min-players` | no, default 1 | Do not draw it with fewer active players than this. |
| `weight` | no, default 1 | Relative likelihood against the other challenges of the same kind. Must be 1 or more. |
| `since` | no | The Prost version that added a shipped entry. Only an update reads it (below); it changes nothing in the game. |

`amount`, `give`, `weight` and `min-players` must be 1 or more. Text lookup per language: the player's language, then
`language.default`, then English, then the first entry. At least one language is needed for `name` and `text`.

### Rewards

| value | what the winner gets |
|---|---|
| `handout` | Hands out `give` sips to others in real life (to the people at the table when nobody else is in the round). |
| `rule` | Makes a real-life rule, picked from `rules.yml` or typed. |
| `joker` | A joker that cancels one drink later (a member holds at most `joker.max`). |
| `next` | Picks the next challenge out of a few cards. |

A Team gets only the group rewards (`rule`, `joker`, `next`) because `handout` makes no sense for a team. A Team entry
that lists none of them gives every participant a joker.

### needs

All listed conditions must hold when the challenge is drawn.

| value | meaning |
|---|---|
| `nether`, `end` | That dimension is open (`dimensions` in `config.yml`). |
| `night` | Overworld time between 13000 and 23000 ticks. |
| `day` | Not night. |
| `rain`, `thunder` | It rains or thunders. |

An entry that targets something that exists only in the Nether or End (nether quartz ore, ancient debris, netherrack,
blazes, ghasts, shulkers, end stone and so on) must list `nether` or `end`, and an entry that makes players enter a
dimension too; otherwise it is skipped with a warning. "Enter the Nether" is not drawn while somebody is already there
(the same for the End). Nor is a reaction somebody cannot do where they are: `water` while somebody is in the Nether,
`eat` while somebody's food bar is full, and `hold` while somebody is in the Nether or the End, unless one of its
`targets` is a block that dimension is made of (netherrack, soul sand, soul soil, gravel, basalt, blackstone or magma
in the Nether, end stone in the End).

### The trackers

| tracker | counts | fields |
|---|---|---|
| `mine` | Blocks broken in survival. Blocks a member placed during the round never count. | `targets` (blocks), `amount` |
| `stat` | The increase of a vanilla statistic since the challenge started. | `stat`, `targets` where the statistic needs them, `amount` |
| `collect` | Items picked up. Items somebody threw never count (no drop-and-pick-up loops). | `targets` (items), `amount` |
| `gift` | Items picked up that another member of the round threw; one per gift. | `targets` (items), `amount` |
| `inventory` | Holds `amount` of the target in the inventory when the time runs out. | `targets` (items), `amount` |
| `count` | `mode: place`: blocks placed (no targets). `mode: consume`: items eaten or drunk (`targets`, empty = anything). | `mode`, `targets`, `amount` |
| `distinct` | How many different things from a `source`; `amount` = how many. Deepslate and stone ore count as the same ore. `biome` counts biomes visited (no targets). | `source`, `targets`, `amount` |
| `reach` | `mode: below` / `above`: a height (`value` = Y). `distance`: blocks away from the start position (`value`). `dimension`: enter `nether` or `the_end` (`value`). `structure`: enter a `village` (`value`). | `mode`, `value` |
| `avoid` | Not doing something until time is up. `mode` is one or more actions, comma separated. Only for `team`, `each` and `solo`. | `mode` |
| `wear` | Seconds spent with the target on the head, added up. | `mode: head`, `targets`, `amount` (seconds) |
| `tower` | Height of the highest block the player placed, counted above where they stood at the start. | `amount` (blocks) |
| `reaction` | The moment a player does the action of `mode`: `crouch` (starts sneaking), `water` (is in water), `chat` (writes `value` in chat, default `Prost`: upper or lower case, spaces around it and colours do not matter, the text is never read as markup), `look_up` (looks straight up), `jump`, `hold` (a `targets` item in the main hand), `sit` (in a boat or minecart, on a mount, or seated by a sit plugin; a seat another plugin refuses does not count), `eat` (finishes eating food), `freeze` (moving after `reaction.freeze-grace-seconds` in `game.yml` breaks it; looking around is fine). Whoever is already in water, looking up or holding the item when it starts has to stop and do it again, and whoever already sits has to get up and sit down again. Only what is done in time counts. Only for `reaction` and `solo` (Solo: done in time wins, else drinks; freeze: not moved wins). No `also-avoid`. | `mode`, `value` (chat), `targets` (hold) |
| `potato` | Hot potato: a marked poisonous potato goes to a random player, who passes it on by dropping it (Q) next to another player. Only another active player can catch it, within `potato.pass-seconds` in `game.yml`; otherwise it jumps back to the thrower. The length is random between half of `seconds` and `seconds` and is not shown (the clock reads `??`); whoever holds it at the end drinks `sips`. The potato cannot be stored (chests, ender chests, bundles, hoppers, item frames, decorated pots), composted, eaten, burnt, destroyed or despawn and is never dropped on death: a holder who dies or leaves passes it to a random other active player. A full inventory hands it to the next player with room (nobody has room: the holder keeps it without the item until their inventory has room). Fewer than two players left cancel it, nobody drinks. Every Prost potato is removed when the challenge ends, on start-up, when a player joins and when its chunk loads; one left in a container (after a crash) goes when a click finds it there or, while a hot potato runs, a hopper moves it. A player's inventory an admin opens (/invsee, OpenInv) is no container: a click on the potato there is only refused. Only for `reaction`, best with `min-players: 2`. No `also-avoid`. | `seconds` (the longest time) |
| `luck` | A game of chance in a menu. Every player who decides gets a menu with the clock; whoever closes it opens it again with the chat button (`/prost play`). Whoever has not decided when `seconds` run out gets a random choice; once everybody has decided, the result is revealed in the menu for `luck.reveal-seconds` in `game.yml`, then the challenge ends. `mode` and the kinds it can be played as: `wheel` (`luck`, `solo`): everyone spins 1-10, the lowest number drinks (a tie: all of them); solo, or the only spinner left: 1-3 drinks, 4-10 wins. `coin` (`duel`): the first drawn player calls heads or tails; a wrong call: the caller drinks, a right one: the other. `chests` (`solo`): three chests hide "drink" (`sips`), a joker (up to `joker.max`) and "hand out" (`give`). `dice` (`solo`): 1-2 drinks, 3-4 nothing, 5-6 hands out `give`. `high_low` (`duel`, `solo`): a number 1-10 is shown, each player guesses whether the next one (never the same) is higher or lower; a wrong guess drinks, a right one wins. `boxes` (`luck`): one box per player, one of them the trap; each takes a free box (a taken box shows its taker's head) and whoever took the trap drinks. A player who drops out keeps nothing (their box is free again). No `also-avoid`. | `mode`, `seconds` (to decide) |
| `vote` | A build contest in three steps. Build: everyone builds for `seconds`; `/prost here` (also a chat button) marks the build where the player stands, otherwise it is where they stand when the time is up. A spot outside the `worlds` of config.yml, in an excluded area or in a Nether or End that is closed (`dimensions` in config.yml or a plugin's gate) is refused (and not taken when the time is up); such a build, or one whose world was unloaded since, is only named in chat and not visited, but can still get votes. Tour: the builds in a random order, `vote.view-seconds` in `game.yml` each, with their world and coordinates; when the builder of the build on show drops out, the tour moves on at once. With `vote.teleport: true` (the default) every player is teleported to each build in turn (out of a vehicle first, and with anything riding on them set down) and afterwards back to where they stood when the tour started. A player another plugin refuses to move to a build is told so and stays where they are; the tour goes on. A spot whose feet or head block is solid is raised to the first two free blocks within 16 above it; with none, the build is not visited but can still get votes. The same for a build in a Nether or End that is closed by its turn; with `vote.teleport: false` its coordinates are still named. From the start of the tour until 3 seconds after they are back and stand on something (the ground, water, a ladder, a vehicle, or flying; 30 seconds after the return at most, so a way back into mid-air is no fall), the players take no damage and cannot change or take anything at a build: no breaking or placing blocks, no buckets, no using blocks or items (doors, buttons, containers, flint and steel, ender pearls), no using, hitting or breaking entities (animals, armor stands, item frames, vehicles, also with arrows), no dropping or picking up items. Fire or frost caught at a build is gone when they are back. Where each player goes back to is written to `returns.yml` before anyone moves: a player who drops out goes back at once, one who quits goes back when they join again, one who dies respawns there, and a server stop brings everyone back; after a crash they go back when they join. A way back that cannot be used yet (a world that is not loaded, a teleport another plugin refuses) is tried again every 5 seconds for a minute while the player stays protected (the tries wait while they ride something); after that minute, or when the server stops, it waits for the next join, and the player is told so; one into a world that no longer exists is dropped with a warning, and the player is told they stay where they are. If `returns.yml` cannot be written, nobody is moved and the builds are only named. An unreadable `returns.yml` at start is moved to `returns.yml.broken` (a warning says how to use it again) instead of being overwritten. Vote: a menu with the other players' heads for `vote.vote-seconds`; one vote, which can be changed until the time is up (a closed menu opens again with `/prost play`). A vote for a player who left is gone. `mode: portrait`: each builder is told privately which other player to portray. Only for `vote` (3 players or more). No `also-avoid`. | `mode` (empty or `portrait`), `seconds` (to build) |
| `proximity` | `mode: all`: all participants within `value` blocks of each other at once (stays reached). `block`: all on the same block (`value` numeric, e.g. 1). `hug`: crouch while another participant is within `value` blocks, `amount` times. Only meaningful for a Team (or `hug` for Each) with `min-players: 2`. | `mode`, `value`, `amount` |

`reach` positions inside an `excluded-areas` box, and anything right after a teleport by a command or plugin, never count.

**Avoid actions:** `damage` (taking any damage), `explosion-damage`, `death`, `jump`, `sprint` (starting to sprint),
`eat` (eating or drinking anything), `chat` (writing in chat), `break` (breaking a block), `kill` (killing a mob),
`water` (being in water), `sleep` (entering a bed).

**Statistics for `stat`:** `KILL_ENTITY` (mobs in `targets`), `CRAFT_ITEM` and `USE_ITEM` (items in `targets`),
`MINE_BLOCK` (blocks in `targets`), and without targets: `ANIMALS_BRED`, `FISH_CAUGHT`, `WALK_ONE_CM`, `SPRINT_ONE_CM`,
`SWIM_ONE_CM`, `CLIMB_ONE_CM`, `CROUCH_ONE_CM`, `BOAT_ONE_CM`, `MINECART_ONE_CM`, `HORSE_ONE_CM`, `FALL_ONE_CM`, `JUMP`,
`SNEAK_TIME`, `SLEEP_IN_BED`, `TRADED_WITH_VILLAGER`, `TALKED_TO_VILLAGER`, `NOTEBLOCK_PLAYED`, `BELL_RING`,
`FLOWER_POTTED`, `TARGET_HIT`, `CAKE_SLICES_EATEN`, `ITEM_ENCHANTED`. Distances (`*_ONE_CM`) are counted in blocks, so
`amount: 1000` is 1000 blocks; `SNEAK_TIME` is counted in seconds.

### Target tags

A target starting with `#` stands for a fixed list; an unknown tag skips the entry.

| tag | contains |
|---|---|
| `#logs` | all log types (oak to pale oak, mangrove, cherry) |
| `#flowers` | the small and tall flowers |
| `#saplings` | all saplings and the mangrove propagule |
| `#wool`, `#beds` | the 16 colours |
| `#hostile` | zombies, skeletons, creepers, spiders, witches, endermen, phantoms, slimes, pillagers, vindicators, guardians and variants |
| `#passive` | cow, pig, sheep, chicken, rabbit, goat |
| `#raw_meat` | beef, porkchop, chicken, mutton, rabbit, cod, salmon |
| `#food` | common foods (bread, apple, cooked meats, baked potato, cookie and others) |
| `#ores_overworld` | coal, copper, iron, gold, redstone, lapis, diamond and emerald ore, stone and deepslate |
| `#zombies` | zombie, husk, drowned, zombie villager |
| `#skeletons` | skeleton, stray, bogged |

### New challenges on an update

When a new Prost version ships challenges, its first start appends them to the end of your `challenges.yml`, under a
comment `# added by Prost <version>`, each as the one line it is in the shipped file. Everything above stays exactly as
it was: your values, your comments and blank lines, the entries you deleted, the line endings and the encoding. A new
challenge whose id your file already has (in any upper or lower case) is not added, so your own entry of that name stays.

`.catalogue-version` next to the file records the version whose challenges it has received, so a new challenge you
delete afterwards is not added again. A `challenges.yml` without it counts as one from Prost 0.3.1. Deleting
`.catalogue-version` makes the next start add every challenge since 0.3.1 that your file lacks. An older Prost
version leaves both files alone. A `.catalogue-version` that holds no version, or cannot be read, adds nothing: the
console warns on each start until it is fixed or deleted.

Nothing is added to a file that is not valid YAML or whose top level is not a map of entries (the console says so); the
challenges follow on the first start or `/prost reload` after you fix it. The same holds for a file whose end would
change the meaning of the appended lines (a closing `...`, or a last text written with `|+`); the console says so too.
If the file cannot be written, the console says so and the next start tries again.

## rules.yml

The list of rules a winner can pick with the `rule` reward ("Nobody may say 'I'", "Drink only with your left hand").
Each item is a map from language code to text; items without any text are skipped.

```yaml
- {en: "Whoever says \"okay\" drinks.", de: "Wer \"okay\" sagt, trinkt."}
- {en: "Everyone talks like a pirate."}
```

The text is shown in the player's language, then the server language, then English, then whatever exists. Rules a
player types are the same for everyone and are shown exactly as typed (colour codes are removed, the length is cut at
`rules.custom-max-length`).
