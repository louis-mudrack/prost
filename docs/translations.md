# Translations

Every text a player or admin sees comes from a language file; nothing is written into the code. English (`en`) and German
(`de`) ship with Prost. Texts of the challenges and rules are not in these files, they carry their own languages
(see [challenges.md](challenges.md)).

## Files

Language files are in `plugins/Prost/lang/`, one per language, named by a language code such as `en` or `de`
(`en.yml`, `de.yml`, `pt.yml`; three-letter ISO codes work too). Region variants such as `pt_br.yml` are not selectable,
because Prost reduces every language to its language part (`pt_BR` and `PT-br` both mean `pt`); the file is
`lang/<code>.yml`.

On every start Prost writes the shipped files if they are missing and appends the keys a new version added to `en.yml`
and `de.yml`, with their comments. Your edits are never overwritten. A language file you added yourself does not get new
keys automatically: texts it lacks are shown in English until you translate them.

Keys are nested YAML, for example `round.opened` or `menu.main.title`. A value is a string or a list of strings (lines of
an item description). Use quotes around text with `:` or leading `<`.

## Which language a player sees

The first of these that exists as a file:

1. a language set by another plugin through the API (a language provider)
2. the player's own choice, `/prost language <code>` (`/prost language auto` removes it)
3. the player's Minecraft client language
4. the server language, `language.default` in `config.yml`

With `language.per-player: false` steps 2 and 3 are skipped, and `/prost language` tells players that the server decides.

For a single text the lookup is: the player's language, then the server language, then English. A text that is missing
everywhere is shown as its key (with one warning in the console), so a missing key is visible rather than empty. A
language file that is not valid YAML, a key that is a section where English has a text (or the reverse), and a value that
is not text are reported with one warning per file; the English text is used in their place. Prost starts regardless.

## MiniMessage

Texts use [MiniMessage](https://docs.advntr.dev/minimessage/format.html): `<red>`, `<bold>`, `<gradient:...>`,
`<click:run_command:'/prost stats'>` and so on.

### Theme tags

The colours of the network feel are set once in `config.yml` (`theme:`) and used in every language file, so a re-colour
needs no translation changes:

| tag | colour |
|---|---|
| `<primary>text</primary>` | the gradient `theme.primary` |
| `<hl>` | `theme.highlight`, for values and emphasis |
| `<ok>` | `theme.ok`, success |
| `<err>` | `theme.error`, penalties and errors |
| `<muted>` | `theme.muted`, side information |

### Value tags

Prost fills in values such as `<player>`, `<sips>`, `<code>`, `<count>` or `<name>`. Which values a text gets is visible
in the English file: **keep every value tag of the English text in your translation**, or the value is lost. Moving a tag
inside the sentence is fine.

Values that came from players (names, typed rules) are inserted as plain text: markup in them is shown literally and
never runs. The same holds for the values of PlaceholderAPI placeholders (`placeholderapi.parse-messages`): a
placeholder whose value contains `<red>` shows `<red>`; only legacy colour codes (`&a`, `§a`) in a value still colour
the text. A `%placeholder%` you write into a language file is resolved for each viewer.

## Subcommand names

Subcommand names are English everywhere (`start`, `invite`, ...). A language file can add its own names in the
`commands:` section, which work for every player, whatever their language:

```yaml
commands:
  invite: [einladen]
  language: [sprache]
```

Each alias is a single word, lowercase. The keys are the English names: `start`, `join`, `invite`, `decline`, `leave`,
`pause`, `status`, `next`, `pace`, `kick`, `end`, `stats`, `language`, `admin`, `reload`, plus the button commands
`reward`, `handout`, `joker` and `round` (the argument of `pause`). A name that already means another subcommand is
skipped with a warning; on a clash between languages the server language wins, then English, then the others in
alphabetical order. Tab completion offers the English names and the player's language.
Aliases for `/prost` itself (`/drinks`) are `commands.aliases` in `config.yml`.

## Adding a language

1. Copy `plugins/Prost/lang/en.yml` to `plugins/Prost/lang/<code>.yml`, for example `pt.yml`.
2. Translate the values. Keep the keys and the value tags, and keep the theme tags.
3. Run `/prost reload`. The language is now available: `/prost language pt`, and players whose client uses it get it
   automatically. To make it the server language, set `language.default: pt` in `config.yml` and reload.
4. Optional: translate the challenges and rules by adding `pt:` to the `name` and `text` of the entries in
   `challenges.yml` and `rules.yml`; until you do, those show in the server language or English.

## Contributing a language

A translation of the shipped texts is welcome as a pull request on GitHub:

1. Add `prost-plugin/src/main/resources/lang/<code>.yml` with exactly the keys and value tags of `en.yml`.
2. Add the code to `SHIPPED` in `prost-plugin/src/main/java/dev/louismudrack/prost/i18n/LangFiles.java`, so servers get
   the file written and extended automatically.
3. Run `./gradlew build`. The parity test checks that a shipped language has the keys and value tags of English; please
   add your language to it.
