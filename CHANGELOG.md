# Changelog

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
