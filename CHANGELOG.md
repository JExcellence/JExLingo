# Changelog

## 0.4.2 - 2026-10-04

- Redesign of chat output, menus and item lore to the suite design rules: suite palette, `/lingo help` with hover
  and click per command, status / stats / inspect / phrases / glossary as centred panels, calmer `[DE » EN]`
  marker that keeps the chat format's colour.
- Fix: menu section titles rendered in the default purple lore colour; filler panes showed an empty tooltip.
- Translation files raised to file-version 3, so live servers get the new texts (old copy kept as `.bak-<time>`).

## 0.4.1 - 2026-10-04

- Fix: lines were only translated when the writer's game language differed from the reader's. A German player
  with an English client was never translated. A local word check (no provider call) now finds German and English
  per line; provider detection is only used when it is unsure and `detection.enabled` is on.
- Fix: colour codes (`&a`, `&#A1F3BE`, `&x&...`, MiniMessage tags) are kept out of the translator and put back in
  the translation; the original line and the hover show the text without codes.
- Fix: `/lingo incoming` failed with "Argument 'mode' ... not found" on servers with the 0.1.0 command file.
  `commands/lingo.yml` and the translation files now carry `# file-version`; an older copy is replaced and kept
  as `.bak-<time>`.
- `/lingo outgoing` and `/lingo original` toggle without an argument; `/lingo incoming` switches to the next mode.
- Warm-up translates every language direction four times at start (one per LibreTranslate worker) and no longer
  counts in the latency figures.

## 0.4.0 - 2026-10-03

- API: `translateFor(writer, reader, text, context)` for player-to-player text, `translateFrom(writer, text,
  target, context)` for relays out of the game, `translateTo(reader, text, source, context)` for text coming into
  the game, `writingLanguageOf(uuid)`. All respect the players' settings and the staff pause.
- Used by JExEssentials (`/msg`, `/r`) and JExDiscord (chat relay both ways); both stay fully working without
  JExLingo.

## 0.3.0 - 2026-10-03

- Daily statistics: totals per day, language pair and source in `jexlingo_daily_stats`, no texts and no players,
  written every five minutes and on shutdown, kept `statistics.retention-days` (default 90).
- `/lingo stats` menu: provider health, overview (translations, characters, share without provider call, share
  shown untranslated), sources, busiest language pairs, provider, learning. Filter today / 7 / 30 days (Premium),
  Bedrock form, chat version for the console.
- Staff tools: `/lingo inspect <player>`, `/lingo set lang|write|incoming|outgoing|original <player> <value>`
  (also for offline players), `/lingo pause [minutes]` and `/lingo resume`, `/lingo ping`, `/lingo cache clear`.
- Learning management (Premium): review menu filter for approved corrections with right-click to take one back,
  `/lingo review revoke <id>`, `/lingo phrases [list|remove <id>]`.
- Placeholders `%jexlingo_paused%`, `%jexlingo_stats_today%`, `%jexlingo_latency%`.
- Internal: core split into learning layer, operations, file handling and staff command builder (class coupling
  under 20); `config-version` 3.
- 6 new unit tests (90 total).

## 0.2.0 - 2026-10-03

- Proprietary license: all rights reserved (LICENSE, README).
- Better translator input: per-language chat slang dictionary (`slang.yml`, reloadable), letter-spam shortening,
  shouting translated in lower case and raised again in the result.
- Writing language separate from the reading language (`/lingo write`, menu card, Bedrock form,
  `%jexlingo_writes%`); chat lines use it as their source language.
- Writing-language learner: samples the first messages of a session and offers the detected language with one
  click (`detection.learn-writing-language`).
- Incoming mode: automatic, on click (`[T]` button and `/lingo show <id>`) or off; replaces the on/off switch.
- Crowd approval (Premium): a correction suggested by enough different players is approved without staff
  (`learning.auto-approve-votes`).
- `/lingo erase <player>` deletes stored settings and open suggestions (right to erasure).
- New options are added to an existing `config.yml` on start, with backup (`config-version` 2).
- Lookup keys ignore trailing `.`/`!` and letter spam, so more lines hit cache, memory and pinned phrases.
- `/lingo status` shows running translations and the slang size.
- 22 new unit tests (84 total).

## 0.1.0 - 2026-10-03

First version.

- Chat translation per viewer through a wrapped chat renderer; the chat event is never cancelled.
- LibreTranslate provider with API key, timeouts, in-flight cap, circuit breaker and health check.
- Pipeline: skip rules, translation memory, pinned phrases, cache, glossary masking, shared provider calls.
- Protected text: URLs, mentions, chat tokens, placeholders, player names, glossary terms.
- Player settings with menu and Bedrock form; onboarding hint.
- Glossary (keep and force terms) with menu, form and commands; default seed file.
- Premium: suggestions, review menu and form, translation memory, pinned phrases, TSV export, custom providers.
- `/lingo` command tree, PlaceholderAPI expansion, public API and events.
- English and German translations.
- Unit tests for masking, breaker, in-flight sharing, provider HTTP client, pipeline, resolver, config, editions.
