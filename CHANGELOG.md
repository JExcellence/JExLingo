# Changelog

## Unreleased

- Fix: a line written in capitals was cached and then shown in capitals to everyone who wrote the same line in
  normal case. Shouted lines now have their own cache entry and never become pinned phrases.
- Fix: online player names that are ordinary words (`Help`, `Base`) were masked in every message, so the
  translator never saw those words. Such names are now only protected when written capitalised or in capitals;
  names like `Steve_42` or `DarkKnight` stay protected in any case.
- Fix: answers like `400 language not supported` or `403 invalid API key` opened the circuit breaker and paused
  translation for every language. Only timeouts, connection errors, 5xx and 429 count now; a pair answered with
  400 is skipped for ten minutes.
- Fix: the local language guess counted `am` and `an` as English, so `bin am spawn` had no guess. Both are left
  out now; more common German and English chat words were added.
- The local language guess also knows French, Spanish, Italian, Dutch, Portuguese and Polish. A word or special
  letter only counts when exactly one enabled language claims it, so `en`, `no` or `do` decide nothing where two
  languages share them.
- When the translator breaks a protected token, the text between the tokens is translated piece by piece instead of
  showing the original. `/lingo status` shows broken and saved lines.
- Pinned phrases (Premium) are dropped when the glossary changes, when `learning.unpin-after-suggestions` players
  suggest a change, when a correction for the same line is approved and after `learning.pin-lifetime-days`.
- `/lingo terms` (Premium): words kept as written in `learning.term-proposal-votes` approved corrections are
  proposed as glossary keep terms; a click fills in the command.
- Masking patterns are compiled once per glossary and set of online names instead of once per line.
- Loading the glossary at start no longer counts as a glossary change.
- Protected text is sent as `Q0Q` tokens instead of `{0}`. Measured on LibreTranslate de to en, `{0}` was dropped in
  three of four lines and `Q0Q` was kept in all of them with natural word order. A token glued to a word (a colour
  code in `&awirklich`) gets a space that is removed again. Fifteen real chat lines through the full pipeline:
  15 translated, no broken tokens.
- Words with an inner underscore (`Steve_42`) are protected even when that player is offline; `/lingo test` uses the
  online names like chat.
- `chat.protect-names`: `SMART` (default), `ALWAYS` or `MENTIONS`.
- Spam protection lets short bursts through: `chat.burst` lines in a row (default 3), then one per
  `chat.player-cooldown-ms`; cache and memory hits never count.
- Language detection takes a slot from the request cap, and a timed-out HTTP call is aborted instead of running on.
- Provider log lines name the address and the next step: unreachable (connection refused, unknown host, no answer),
  the exact `--load-only` value for missing models, and a rejected API key once. A warning when `provider.url` is
  plain http to another machine.
- `/lingo status` and the stats show `-` instead of `-1 ms` and `100%` before there is data.
- Daily statistics keep broken and saved tokens and, per language pair, the corrections per 1,000 translations.
- `/lingo terms` (Premium) also proposes force terms: words players replaced the same way in several lines, scoped to
  the language pair. `/lingo glossary add` accepts a `de>en:` prefix for that.
- `/lingo glossary import` adds new terms from `glossary.yml`; existing terms stay.
- New subcommands reach existing `commands/lingo.yml` files, not only new top-level commands.
- New servers get a glossary that fits any server (chat slang, Spawn, Nether, Creeper ...) instead of island terms.
- Release jars no longer contain demo translations, configs and files of the bundled libraries; JExLingo ships its
  own `translations/config.yml`.
- The bundled database config offers only H2 and MySQL/MariaDB, whose drivers are downloaded, with a smaller pool.
- No SLF4J "no providers" lines in the console.
- `docs/SELF_HOSTING.md` walks from an empty server to translated chat; the LibreTranslate compose file reads its
  languages, port and memory from `.env`.
- New database columns (`jexlingo_daily_stats`: token_losses, recoveries, corrections; `jexlingo_memory`:
  machine_text) are added on start.
- Lines that differ only in apostrophes, commas or repeated `?` share corrections, pins and cache entries
  (`wie geht's` and `wie gehts`); a single `?` still counts.
- English translations write the pronoun `I` with a capital letter (`i sell [item]` becomes `I sell [item]`).
- A shouted line gets its approved correction or pinned phrase in capitals too.
- `config.yml` raised to config-version 6 with the new `learning` and `chat` options.

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
