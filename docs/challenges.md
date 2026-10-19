# Challenges and rules

`challenges.yml` is the challenge catalogue and `rules.yml` the list of prepared rules. Prost writes both once (113
challenges, 30 rules); after that they are yours: edit, delete and add as you like. Neither file is touched by updates.
`/prost reload` reads them again.

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
| `reaction` | 2 | Everyone does the action as fast as they can. Whoever has not done it when the time runs out drinks; when everybody has, the last one drinks (an exact tie: all of them). It ends as soon as everybody has done it. `freeze`: whoever moves drinks; when nobody moves, everybody wins. Hot potato (`potato` tracker): whoever holds the potato when the hidden time runs out drinks, the others win. Only with the `reaction` and `potato` trackers. |

**One penalty per challenge:** when anybody drinks, the winners get no reward on top. Rewards are only given when
nobody drinks, so in a Race, a Duel or a Reaction there is normally no reward (a Reaction rewards only a freeze nobody broke) (somebody always loses; only when the others left mid-challenge and one participant remains, the lone winner is rewarded). This is not configurable.

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
| `tracker` | yes | How progress is counted, one of the 13 below. |
| `targets` | per tracker | The blocks, items or mobs that count, as Bukkit names (`IRON_ORE`, `ZOMBIE`), or `#tags` (below). Names are checked against the running server; an unknown name skips the entry. |
| `stat` | `stat` tracker | One or more vanilla statistics, comma separated: `"WALK_ONE_CM, SPRINT_ONE_CM"` adds them up. |
| `source` | `distinct` tracker | What is counted: `mine`, `collect`, `consume`, `craft`, `kill` or `biome`. |
| `mode` | some trackers | Sub-type of the tracker, see the table. |
| `value` | some trackers | A number or name, depending on the tracker. |
| `amount` | no, default 1 | The goal, in the tracker's unit. Per player; for Team multiplied by the number of players. |
| `seconds` | yes | Time limit, 10 to 900. |
| `sips` | yes | The fixed penalty, 1 to 6. Shown at the start. |
| `give` | no, default `sips` | Sips a winner may hand out with the `handout` reward. |
| `kinds` | yes | Which kinds this challenge may be played as: `team`, `each`, `race`, `duel`, `solo`, `reaction`. |
| `rewards` | yes | What winners can choose from, see below. |
| `needs` | no | Conditions for drawing it, see below. |
| `also-avoid` | no | Actions that break the challenge even if the goal is reached (for example kill a creeper without explosion damage). Same names as the `avoid` modes. |
| `min-players` | no, default 1 | Do not draw it with fewer active players than this. |
| `weight` | no, default 1 | Relative likelihood against the other challenges of the same kind. Must be 1 or more. |

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
(the same for the End).

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
| `reaction` | The moment a player does the action of `mode`: `crouch` (starts sneaking), `water` (is in water), `chat` (writes `value` in chat, default `Prost`: upper or lower case, spaces around it and colours do not matter, the text is never read as markup), `look_up` (looks straight up), `jump`, `hold` (a `targets` item in the main hand), `sit` (in a boat or minecart, on a mount, or seated by a sit plugin), `eat` (finishes eating food), `freeze` (moving after `reaction.freeze-grace-seconds` in `game.yml` breaks it; looking around is fine). Only for `reaction` and `solo` (Solo: done in time wins, else drinks; freeze: not moved wins). No `also-avoid`. | `mode`, `value` (chat), `targets` (hold) |
| `potato` | Hot potato: a marked poisonous potato goes to a random player, who passes it on by dropping it (Q) next to another player. Only another active player can catch it, within `potato.pass-seconds` in `game.yml`; otherwise it jumps back to the thrower. The length is random between half of `seconds` and `seconds` and is not shown (the clock reads `??`); whoever holds it at the end drinks `sips`. The potato cannot be stored (chests, ender chests, bundles, hoppers, item frames, decorated pots), composted, eaten, burnt, destroyed or despawn and is never dropped on death: a holder who dies or leaves passes it to a random other active player. A full inventory hands it to the next player with room (nobody has room: the holder keeps it without the item until their inventory has room). Fewer than two players left cancel it, nobody drinks. Every Prost potato is removed when the challenge ends, on start-up, when a player joins and when its chunk loads. Only for `reaction`, best with `min-players: 2`. No `also-avoid`. | `seconds` (the longest time) |
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
