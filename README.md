# JExLingo

> **Status:** 0.1.0 built, not yet deployed or tested in game. **Last verified:** 2026-10-03.
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
- **Glossary**: keep server words as written (`OneBlock`, `gg`) or force a fixed translation (`Insel` -> `Island`).
- **Player settings** (`/lingo`): reading language (`auto` follows the game language), translate incoming,
  translate my messages (off = the text never leaves the server), show the original as a second line.
- **Bedrock**: every menu has a Cumulus form; Bedrock viewers get the original as a second line instead of hover.
- **Learning (Premium)**: players click a translated line to suggest a better translation, staff approve it, and it
  answers that line from then on. Frequent short phrases get their translation pinned. Approved pairs export as TSV
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
2. Put `JExLingo-0.1.0-Free.jar` or `-Premium.jar` into `plugins/` (Paper 26.x, Java 25).
3. Optional: `export JEXLINGO_API_KEY=<key>` for the server process.
4. Start the server, then `/lingo status` shows whether the provider is reachable.

## Commands

| Command | Permission | Default |
|---|---|---|
| `/lingo` | `jexlingo.use` | everyone |
| `/lingo lang <auto\|code>`, `incoming`, `outgoing`, `original <enable\|disable>` | `jexlingo.use` | everyone |
| `/lingo suggest [id] [text]` | `jexlingo.suggest` | everyone (Premium) |
| `/lingo review [approve\|reject\|block\|unblock]` | `jexlingo.review` | op (Premium) |
| `/lingo glossary [add\|remove\|list]` | `jexlingo.admin.glossary` | op |
| `/lingo test <from> <to> <text>`, `status`, `export`, `reload` | `jexlingo.admin` | op |

The tree lives in `plugins/JExLingo/commands/lingo.yml`; rename the command or change permissions there.

## Placeholders

`%jexlingo_language%`, `%jexlingo_language_upper%`, `%jexlingo_preference%`, `%jexlingo_incoming%`,
`%jexlingo_outgoing%`, `%jexlingo_original%`, `%jexlingo_provider%`.

## Building

JExLingo builds inside [JExSuite](https://github.com/JExcellence) (it uses the suite's Gradle conventions and version
catalog):

```bash
./gradlew :JExLingo:buildAll --no-parallel --max-workers=1
```

Jars: `JExLingo/jexlingo-free/build/libs/` and `JExLingo/jexlingo-premium/build/libs/`.

## Privacy

Chat lines are processed in memory only and sent to your own LibreTranslate instance. No chat history is stored.
See [docs/PRIVACY.md](docs/PRIVACY.md) for a text block for your privacy notice.
