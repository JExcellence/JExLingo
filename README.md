# JExLingo

Live chat translation for Paper servers. Players write in their language, and every viewer reads the line in
theirs. Translation runs on your own LibreTranslate instance: after the first model download, no chat line leaves
your machine.

```
[DE » EN] <Steve> Does anyone have iron to sell?      hover: Original (DE): Hat jemand Eisen zu verkaufen?
```

> **Status:** 0.4.2 with the changes in [CHANGELOG.md](CHANGELOG.md). Unit tested and checked on a clean Paper 26.3
> server; the in-game checks with real clients are listed in [docs/TEST_CHECKLIST.md](docs/TEST_CHECKLIST.md).
> **License:** proprietary, see [LICENSE](LICENSE).

## How a line travels

```
player writes ─► language check (local word lists, no network)
              ─► one translation per reader language, shared by every reader of it
                   approved corrections ─► pinned phrases ─► glossary ─► cache ─► LibreTranslate
              ─► each reader sees the line in their language, the original on hover
```

The chat event is never cancelled and the server never waits on the translator: if anything is slow or down, the
original line is shown and `/lingo status` says why.

## Features

**Chat**
- Works with your chat format: JExLingo swaps only the message text inside the active chat renderer, so ranks,
  colours, signed chat, reports and relays keep working.
- Reading language follows the game language (`auto`) or a choice in `/lingo`; writing language is detected per line
  for German, English, French, Spanish, Italian, Dutch, Portuguese and Polish, learned from the first messages, or
  set with `/lingo write`.
- Per player: translate incoming lines automatically, on click (`[T]` button) or never; keep own messages off the
  translator; show the original as a second line.
- Bedrock players get every menu as a form and the original as a second line.

**Translation quality**
- Protected parts never reach the translator: links, `@mentions`, `[item]` tokens, `{placeholders}`, colour codes,
  player names (rules in `chat.protect-names`) and glossary terms. They travel as `Q0Q` tokens, which LibreTranslate
  keeps like names; broken answers are retried piece by piece.
- Chat slang is expanded before translation (`idk`, `vllt`, editable in `slang.yml`), letter spam is shortened,
  shouting is translated and raised again, English gets its capital `I`.
- Glossary: keep words as written (`Spawn`, your server name) or force a translation, optionally for one language
  pair (`de>en:Truhe` → `chest`). `/lingo glossary import` reads new terms from `glossary.yml`.

**Learning (Premium)**
- Players click a translated line to suggest a better one; staff approve it, or it is approved when enough players
  suggest the same text. It answers that line, and lines that differ only in commas or apostrophes, from then on.
- Frequent short lines get their translation pinned. A pin is dropped after a glossary edit, when players suggest a
  change, when a correction is approved, and after 30 days.
- `/lingo terms` turns repeated corrections into glossary proposals: words players keep as written, and words they
  replace the same way in several lines. One click fills in the command.
- Approved pairs export as TSV for offline model training.

**For staff**
- `/lingo status`: provider health with the exact fix in the log (address, missing models with the `--load-only`
  value, rejected key), latency, cache, results by source, broken tokens.
- `/lingo stats`: daily totals per language pair and source, corrections per 1,000 translations; no texts and no
  players are stored.
- Inspect and change any player's settings, pause translation during maintenance, test a line with `/lingo test`.

**For developers**
- `JExLingoApi` (`translate`, `translateFor`, `translateFrom`, `translateTo`, `languageOf`, `writingLanguageOf`),
  `ChatTranslatedEvent`, `TranslationSuggestedEvent`, and a `TranslationProvider` interface for other backends.

## Languages

| What | Languages |
|---|---|
| Chat translation | every language your LibreTranslate loads (`LT_LANGUAGES` in `.env`) and `languages.enabled` allows |
| Plugin texts (menus, help, replies) | English and German included; add any other with a file |

To use another language for the plugin texts, copy `translations/en_US.yml` to `translations/fr_FR.yml` (or any
Minecraft locale), translate it and restart; missing texts are filled in English. To share it with everyone, see
[docs/TRANSLATING.md](docs/TRANSLATING.md). Requests for new languages are tagged
[`translation`](https://github.com/JExcellence/JExLingo/issues?q=is%3Aopen+label%3Atranslation).

## Editions

| | Free | Premium |
|---|---|---|
| Chat translation, protected text, settings, Bedrock forms | yes | yes |
| Chat languages | 3 | unlimited |
| Glossary terms | 50 | unlimited |
| Corrections, pinned phrases, term proposals, TSV export | - | yes |
| Providers registered by other plugins | - | yes |
| Statistics | today | today, 7 and 30 days |

## Quick start

1. Start LibreTranslate next to the server: copy [deploy/libretranslate](deploy/libretranslate/README.md), set your
   languages in `.env`, run `docker compose up -d`.
2. Put `JExLingo-<version>-Free.jar` or `-Premium.jar` into `plugins/` (Paper 26.x or Folia, Java 25). No other
   plugin and no database setup needed.
3. Give the server process the key: `export JEXLINGO_API_KEY=<key from ltmanage keys add 600>`.
4. Start the server and run `/lingo status`, then `/lingo test de en Hat jemand Eisen?`.

[docs/SELF_HOSTING.md](docs/SELF_HOSTING.md) walks through it with requirements, checks and a fix for every log
message. All options are in [docs/CONFIG.md](docs/CONFIG.md).

## Commands

| Command | Permission | Default |
|---|---|---|
| `/lingo` (menu), `lang`, `write`, `incoming`, `outgoing`, `original`, `show <id>`, `help` | `jexlingo.use` | everyone |
| `/lingo suggest [id] [text]` | `jexlingo.suggest` | everyone (Premium) |
| `/lingo review [approve\|reject\|revoke\|block\|unblock]`, `phrases [list\|remove]`, `terms` | `jexlingo.review` | op (Premium) |
| `/lingo glossary [add\|remove\|list\|import]` | `jexlingo.admin.glossary` | op |
| `/lingo stats [today\|week\|month]` | `jexlingo.admin.stats` | op |
| `/lingo status`, `test`, `ping`, `pause`, `resume`, `cache clear`, `inspect`, `set`, `export`, `erase`, `reload` | `jexlingo.admin` | op |

Rename the command or change permissions in `plugins/JExLingo/commands/lingo.yml`; new subcommands from updates are
added to it automatically.

## When a line stays untranslated

The original is shown whenever a translation is not safe to show. `/lingo status` points to the reason:

| What you see | Cause | What helps |
|---|---|---|
| Circuit breaker open, failed requests rising | LibreTranslate is down or too slow | Check the container; raise `provider.request-timeout-ms` on slow hardware |
| Refused requests rising | More lines at once than `provider.max-concurrent-requests`, or a rejected pair | Raise the limit; load the model named under "Missing models" |
| Broken tokens higher than saved | The translator damaged names or links and the retry failed too | Add frequent words to the glossary |
| Many "Already in the language" | The line was read as the reader's language | The writer sets `/lingo write <code>`, or turn on `detection.enabled` |
| One player's lines never change | `/lingo outgoing` is off for them, or they send more than `chat.burst` lines in a row | `/lingo inspect <player>` |

## Placeholders

`%jexlingo_language%`, `%jexlingo_language_upper%`, `%jexlingo_preference%`, `%jexlingo_writes%`,
`%jexlingo_incoming%`, `%jexlingo_outgoing%`, `%jexlingo_original%`, `%jexlingo_provider%`, `%jexlingo_paused%`,
`%jexlingo_stats_today%`, `%jexlingo_latency%`.

## Suite integrations

Optional, only when the plugins are installed: private messages (`/msg`, `/r`) reach the receiver in their language,
and a Discord chat relay posts game lines in the channel language and shows Discord lines to each player in theirs.
Both respect each player's settings and the staff pause.

## Privacy

Chat lines are processed in memory and sent only to your own LibreTranslate. No chat history is stored; the cache
lives in memory, statistics hold numbers only. [docs/PRIVACY.md](docs/PRIVACY.md) has a text block for your privacy
notice.

## Building

JExLingo builds inside its parent project, which provides the shared Gradle conventions and version catalog:

```bash
./gradlew :JExLingo:buildAll
```

The jars land in `JExLingo/jexlingo-free/build/libs/` and `JExLingo/jexlingo-premium/build/libs/`.

## License

JExLingo is proprietary software. Copyright (c) 2026 JExcellence, all rights reserved. The source is visible for
reference only; copying, modifying, redistributing or building it, and using it in other software, are not
permitted without written permission. Running official releases follows the terms of the edition you obtained.
Pull requests and issues do not grant any rights. Full text: [LICENSE](LICENSE).
