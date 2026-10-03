# JExLingo

> **Status:** 0.2.0 built, not yet deployed or tested in game. **Last verified:** 2026-10-03.
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
  failure the original line is shown.
- **Protected text**: URLs, `@mentions`, `[item]`-style chat tokens, `{placeholders}`, online player names and
  glossary terms are masked before translation and restored after. A line whose tokens come back broken is shown
  untranslated.
- **Better input for the translator**: chat slang is expanded per language (`idk` -> `I don't know`,
  `vllt` -> `vielleicht`, editable in `slang.yml`), letter spam is shortened and shouting is translated in
  lower case and raised again, so `HILF MIR` becomes `HELP ME`.
- **Writing language**: players who chat in another language than their game client set `/lingo write de`;
  JExLingo also learns it from their first messages and offers it with one click.
- **On-demand mode**: `/lingo incoming click` shows lines as written with a small `[T]` button that translates
  that one line, which also saves translator load.
- **Glossary**: keep server words as written (`OneBlock`, `gg`) or force a fixed translation (`Insel` -> `Island`).
- **Player settings** (`/lingo`): reading language (`auto` follows the game language), translate incoming,
  translate my messages (off = the text never leaves the server), show the original as a second line.
- **Bedrock**: every menu has a Cumulus form; Bedrock viewers get the original as a second line instead of hover.
- **Learning (Premium)**: players click a translated line to suggest a better translation, staff approve it, and it
  answers that line from then on. When several different players suggest the same correction, it is approved
  without staff (`learning.auto-approve-votes`). Frequent short phrases get their translation pinned. Approved pairs export as TSV
  for offline model training.
- **API** for other plugins: `JExLingoApi.translate(...)`, `languageOf(uuid)`, `ChatTranslatedEvent`, and a
  `TranslationProvider` SPI for other backends.

## Editions

| | Free | Premium |
|---|---|---|
| Chat translation, hover original, settings, Bedrock forms | yes | yes |
| Languages | 3 | unlimited |
| Glossary terms | 50 | unlimited |
| Corrections, translation memory, pinned phrases, TSV export | - | yes |
| Providers registered by other plugins | - | yes |

## Quick start

1. Run LibreTranslate on the same machine: [deploy/libretranslate](deploy/libretranslate/README.md).
2. Put `JExLingo-0.2.0-Free.jar` or `-Premium.jar` into `plugins/` (Paper 26.x, Java 25).
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
| `/lingo test <from> <to> <text>`, `status`, `export`, `reload`, `erase <player>` | `jexlingo.admin` | op |

The tree lives in `plugins/JExLingo/commands/lingo.yml`; rename the command or change permissions there.

## Placeholders

`%jexlingo_language%`, `%jexlingo_language_upper%`, `%jexlingo_preference%`, `%jexlingo_writes%`, `%jexlingo_incoming%`,
`%jexlingo_outgoing%`, `%jexlingo_original%`, `%jexlingo_provider%`.

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
