# JExLingo

> **Status:** 0.4.2 plus the unreleased fixes in [CHANGELOG.md](CHANGELOG.md) built and unit tested, not yet
> tested in game. **Last verified:** 2026-11-02.
> **License:** proprietary, all rights reserved. See [LICENSE](LICENSE).
> **Related:** [docs/PLAN.md](docs/PLAN.md), [docs/CONFIG.md](docs/CONFIG.md), [docs/PRIVACY.md](docs/PRIVACY.md),
> [docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md), [deploy/libretranslate](deploy/libretranslate/README.md).

Live chat translation for Paper servers. Players write in their language; every viewer reads the line in theirs.
Translation runs on your own LibreTranslate instance, offline, with no third-party service.

```
[DE » EN] <Steve> Does anyone have iron to sell?      <- hover: "Original (DE): Hat jemand Eisen zu verkaufen?"
```

## Features

- **Per-viewer translation** that keeps your chat format: JExLingo wraps the active chat renderer (JExEssentials or
  Paper's default) and only swaps the message text. The chat event is never cancelled, so signed chat, reports,
  Discord relays and logs keep working.
- **One provider call per target language**, shared by every viewer of that language; identical lines running at
  the same time share one call; results are cached in memory.
- **Never blocks the server**: async HTTP, a cap on requests in flight, a circuit breaker and a hard timeout. On any
  failure the original line is shown. Only real outages (timeouts, connection errors, 5xx, rate limits) pause the
  provider; a language pair it rejects is skipped for ten minutes without touching the other pairs.
- **Protected text**: URLs, `@mentions`, `[item]`-style chat tokens, `{placeholders}`, online player names and
  glossary terms are masked before translation and restored after. Names that are ordinary words (`Max`, `Help`)
  are only protected when written capitalised or in capitals, so `can someone help me` stays translatable while a
  player called `Help` is online. When the translator breaks a token, the text between the tokens is translated
  piece by piece instead of giving up; `/lingo status` shows how often that happens.
- **Better input for the translator**: chat slang is expanded per language (`idk` -> `I don't know`,
  `vllt` -> `vielleicht`, editable in `slang.yml`), letter spam is shortened and shouting is translated in
  lower case and raised again, so `HILF MIR` becomes `HELP ME`. Shouted and normal lines have separate cache
  entries.
- **Writing language**: every line is checked against common words and letters of German, English, French,
  Spanish, Italian, Dutch, Portuguese and Polish without a provider call, so a German player on an English client
  is translated from German. Players can also set `/lingo write de`; JExLingo learns it from their first messages
  and offers it with one click.
- **On-demand mode**: `/lingo incoming click` shows lines as written with a small `[T]` button that translates
  that one line, which also saves translator load.
- **Glossary**: keep server words as written (`OneBlock`, `gg`) or force a fixed translation (`Insel` -> `Island`).
- **Player settings** (`/lingo`): reading language (`auto` follows the game language), translate incoming,
  translate my messages (off = the text never leaves the server), show the original as a second line.
- **Bedrock**: every menu has a Cumulus form; Bedrock viewers get the original as a second line instead of hover.
- **Learning (Premium)**: players click a translated line to suggest a better translation, staff approve it, and it
  answers that line from then on. When several different players suggest the same correction, it is approved
  without staff (`learning.auto-approve-votes`). Frequent short phrases get their translation pinned; a pin is
  dropped when the glossary changes, when two players suggest a change to it, when a correction for it is approved
  and after 30 days, so a wrong machine translation does not stay forever. Words players keep as written in
  several corrections (`Spawn` instead of `Laichen`) are proposed as glossary terms in `/lingo terms`, one click to
  add. Approved pairs export as TSV for offline model training.
- **Statistics**: daily totals per language pair and source (no texts, no players) in a menu with provider
  health, sources, busiest pairs and learning numbers; `/lingo stats` in chat for the console. Today in Free, the
  last 7 and 30 days in Premium.
- **Staff tools**: inspect and change any player's settings, pause chat translation during provider maintenance,
  ping the provider, clear the cache, take back approved corrections and unpin wrong phrases.
- **Suite integrations**: with JExEssentials, `/msg` and `/r` reach the receiver in their language (original on
  hover); with JExDiscord, the chat relay posts game lines in the channel language (original below) and shows
  Discord lines to every player in their language. Both respect each player's settings and the staff pause.
- **API** for other plugins: `JExLingoApi.translate(...)`, `translateFor(writer, reader, ...)`, `translateFrom(writer, ...)`,
  `translateTo(reader, ...)`, `languageOf(uuid)`, `writingLanguageOf(uuid)`, `ChatTranslatedEvent`, and a
  `TranslationProvider` SPI for other backends.

## Editions

| | Free | Premium |
|---|---|---|
| Chat translation, hover original, settings, Bedrock forms | yes | yes |
| Languages | 3 | unlimited |
| Glossary terms | 50 | unlimited |
| Corrections, translation memory, pinned phrases, TSV export | - | yes |
| Providers registered by other plugins | - | yes |
| Statistics | today | today, 7 and 30 days |

## Quick start

1. Run LibreTranslate on the same machine: [deploy/libretranslate](deploy/libretranslate/README.md).
2. Put `JExLingo-0.4.2-Free.jar` or `-Premium.jar` into `plugins/` (Paper 26.x, Java 25).
3. Optional: `export JEXLINGO_API_KEY=<key>` for the server process.
4. Start the server, then `/lingo status` shows whether the provider is reachable.

## Commands

| Command | Permission | Default |
|---|---|---|
| `/lingo` | `jexlingo.use` | everyone |
| `/lingo lang <auto\|code>`, `write <auto\|code>` | `jexlingo.use` | everyone |
| `/lingo incoming <auto\|click\|off>`, `outgoing`, `original <enable\|disable>`, `show <id>` | `jexlingo.use` | everyone |
| `/lingo suggest [id] [text]` | `jexlingo.suggest` | everyone (Premium) |
| `/lingo review [approve\|reject\|block\|unblock]` | `jexlingo.review` | op (Premium) |
| `/lingo glossary [add\|remove\|list]` | `jexlingo.admin.glossary` | op |
| `/lingo stats [today\|week\|month]` | `jexlingo.admin.stats` | op |
| `/lingo inspect <player>`, `set <lang\|write\|incoming\|outgoing\|original> <player> <value>` | `jexlingo.admin` | op |
| `/lingo pause [minutes]`, `resume`, `ping`, `cache clear` | `jexlingo.admin` | op |
| `/lingo review revoke <id>`, `phrases [list\|remove <id>]`, `terms` | `jexlingo.review` | op (Premium) |
| `/lingo test <from> <to> <text>`, `status`, `export`, `reload`, `erase <player>` | `jexlingo.admin` | op |

The tree lives in `plugins/JExLingo/commands/lingo.yml`; rename the command or change permissions there.

## When a line stays untranslated

The original line is shown whenever a translation is not safe to show. `/lingo status` tells you which case it is:

| What you see in `/lingo status` | Cause | What helps |
|---|---|---|
| Circuit breaker open, failed requests rising | LibreTranslate is down or too slow | Check the container; raise `provider.request-timeout-ms` on slow hardware |
| Refused requests rising | More lines at once than `provider.max-concurrent-requests`, or a rejected pair | Raise the limit; install the missing model shown under "Missing models" |
| Broken tokens higher than saved | The translator damaged names or links and the piece-by-piece retry failed too | Add frequent words to the glossary; report the line |
| Results by source: many "Already in the language" | The line was read as the reader's language | The writer sets `/lingo write <code>`, or turn on `detection.enabled` |
| Nothing, but one player's lines never change | That player turned off `/lingo outgoing`, or writes faster than `chat.player-cooldown-ms` | `/lingo inspect <player>` |

## Placeholders

`%jexlingo_language%`, `%jexlingo_language_upper%`, `%jexlingo_preference%`, `%jexlingo_writes%`, `%jexlingo_incoming%`,
`%jexlingo_outgoing%`, `%jexlingo_original%`, `%jexlingo_provider%`, `%jexlingo_paused%`, `%jexlingo_stats_today%`,
`%jexlingo_latency%`.

## Building

JExLingo builds inside [JExSuite](https://github.com/JExcellence) (it uses the suite's Gradle conventions and version
catalog):

```bash
./gradlew :JExLingo:buildAll --no-parallel --max-workers=1
```

Jars: `JExLingo/jexlingo-free/build/libs/` and `JExLingo/jexlingo-premium/build/libs/`.

## License

JExLingo is proprietary software. Copyright (c) 2026 JExcellence, all rights reserved. The source is visible for
reference only; copying, modifying, redistributing or building it, and using it in other software, are not
permitted without written permission. Running official releases follows the terms of the edition you obtained.
Pull requests and issues do not grant any rights. Full text: [LICENSE](LICENSE).

## Privacy

Chat lines are processed in memory only and sent to your own LibreTranslate instance. No chat history is stored.
See [docs/PRIVACY.md](docs/PRIVACY.md) for a text block for your privacy notice.
