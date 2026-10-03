# JExLingo - Implementation Plan

> **Status:** 0.4.0 built (phases 1-8 plus sections 20-22), phase 0 benchmark and in-game test open. **Created:** 2026-10-03.
> **Last verified:** 2026-10-03. **Owner:** JExcellence.
> **Related:** [../../CLAUDE.md](../../CLAUDE.md), [../../docs/AGENT_BRIEF.md](../../docs/AGENT_BRIEF.md),
> [../../docs/JEXCELLENCE_FRAMEWORK_USAGE.md](../../docs/JEXCELLENCE_FRAMEWORK_USAGE.md),
> [../../ADDING_A_PLUGIN.md](../../ADDING_A_PLUGIN.md). Reference plugin for layout and wiring: JExVote.

Live chat translation for Paper servers. Players write in their language, every viewer reads the message in
theirs. Translation runs on a self-hosted LibreTranslate instance, fully offline, no third-party service.
The plugin learns server vocabulary through a glossary, approved corrections and a phrase cache.

Markers used in this document:
- **[VERIFY]** - assumption or unchecked fact; must be confirmed before or during the phase that needs it.
- **[DECISION]** - owner decision still open.

---

## 1. Goals and non-goals

### Goals
1. Chat between German and English players works without either side thinking about it.
2. Never block the main thread or any region thread. Never lose a chat message: on any failure the original is shown.
3. Fully self-hostable on one Debian machine with 4-8 GB free RAM.
4. Languages are configurable; de/en is only the default.
5. Gets better over time through server-specific vocabulary and approved corrections, not through guessing.
6. Public-ready: clean code (Sonar gate of the suite), documented, free edition usable on its own.
7. First deployment target: the owner's own server. Hosting for other servers comes later.

### Non-goals (for now)
- Translating signs, books, item names or GUI text of other plugins.
- Cloud translation providers (DeepL, Google). The provider interface allows them later.
- Automatic model training inside the plugin. Training stays an offline, manual step (section 9.5).
- Hosting a translation service for other server owners (section 13, later phase).

---

## 2. Changes against the original prompt

| Original idea | Problem | JExLingo approach |
|---|---|---|
| Cancel `AsyncChatEvent`, loop over players, send manually | Breaks signed chat and chat reporting, bypasses moderation, chat formatters (JExEssentials), Discord relay (JExDiscord) and chat logs | Keep the event. Wrap the existing `ChatRenderer` and only swap the message component per viewer (section 6.2) |
| One API call per recipient | With 2 languages and 50 players that is up to 49 calls for one line | One call per distinct target language, shared by all viewers of that language |
| Language = `player.locale()` | Many German players use an English client | Stored per-player preference, default `auto` = client locale; optional detection |
| Hard-coded `http://localhost:5000` | Not usable for anyone else | Configurable URL, optional API key, health check |
| Translated text used as formatted text | A player can inject `<click:run_command:...>` that survives translation | Translated text is only ever `Component.text(...)`, never parsed as MiniMessage |
| Format `[DE -> EN] Name: Text` | `->` is not in the approved symbol vocabulary | `[DE » EN]` marker from translation files, the rest from the server's existing chat format |
| `api-version '1.21'` for Paper 26.3 | Suite catalog is `paper = 26.2.build.87-stable`; 26.3 status unknown | Build against the catalog version; `api-version: '1.21'` like JExVote **[VERIFY]** when the catalog moves to 26.3 |

---

## 3. Self-hosting on Debian

### 3.1 Feasibility
- LibreTranslate is open source (AGPL-3.0) and runs Argos Translate models locally. No outbound calls after the models
  are downloaded.
- AGPL does not reach the plugin: the plugin talks to it over HTTP. Only modifications to LibreTranslate itself that
  are offered as a network service must be published.
- Memory for en+de only: estimated 1-2.5 GB **[VERIFY with `docker stats` in phase 0]**. This fits 4-8 GB free.
- CPU only is fine. Argos uses CTranslate2, which benefits from AVX2 **[VERIFY CPU flags]**.
- Latency for a short chat line on CPU: estimated 100-500 ms **[VERIFY with the benchmark in phase 0]**.

### 3.2 Host information needed
Run on the Debian machine and paste the output:

```bash
{ grep -E 'PRETTY_NAME|VERSION_CODENAME' /etc/os-release; uname -r; nproc; lscpu | grep -E 'Model name|Thread|Socket'; grep -q avx2 /proc/cpuinfo && echo "AVX2: yes" || echo "AVX2: no"; free -h; df -h /; docker --version; docker compose version; lspci 2>/dev/null | grep -iE 'vga|3d|nvidia'; java -version 2>&1 | head -1; ss -ltn | grep -E ':25565|:5000'; } 2>&1
```

Note: Debian 15 is, as far as known here, not released (13 "trixie" is stable since 2025). The command shows the
real version **[VERIFY]**.

### 3.3 Docker Compose (owner setup)

`deploy/libretranslate/docker-compose.yml` in the repo:

```yaml
services:
  libretranslate:
    image: libretranslate/libretranslate:latest
    container_name: jexlingo-libretranslate
    restart: unless-stopped
    command: ["--load-only", "en,de", "--char-limit", "500", "--req-limit", "600", "--api-keys"]
    ports:
      - "127.0.0.1:5000:5000"
    volumes:
      - lt-models:/home/libretranslate/.local
      - lt-db:/app/db
    mem_limit: 3g
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:5000/languages || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
volumes:
  lt-models:
  lt-db:
```

Notes:
- `--load-only` is passed as a CLI argument on purpose: the `LT_LOAD_ONLY` environment variable is not split on
  commas by LibreTranslate and stays one string (confirmed in the LibreTranslate configuration docs).
- Whether `command:` appends arguments to the image entrypoint, and whether `wget` exists in the image
  **[VERIFY in phase 0]**.
- `127.0.0.1` binding: only reachable from the same machine. If the Minecraft server runs on another host, use
  WireGuard/Tailscale or Caddy with TLS plus an API key. Never expose port 5000 directly. Firewall with `ufw`.
- API key for the plugin: `docker exec jexlingo-libretranslate ltmanage keys add 600` (requests per minute).
- Adding a language later: extend `--load-only`, restart, add it to the plugin config.

### 3.4 Benchmark script
`deploy/libretranslate/bench.sh`: sends 200 typical chat lines (de->en and en->de), prints p50/p95 latency and
memory. Used to set the plugin defaults (`inline-wait`, concurrency) from real numbers instead of guesses.

---

## 4. Repository, modules and editions

### 4.1 Repository
- New public GitHub repo `JExcellence/JExLingo`, added to JExSuite as a git submodule (same pattern as JExVote,
  JExOneblock). `settings.gradle.kts` includes the four modules.
- Builds inside JExSuite only (uses `buildSrc` conventions and the version catalog), exactly like JExVote. The
  shaded JAR contains JExPlatform, JExTranslate, JExCommand, JEHibernate, ConfigMapper; JExDependency downloads the
  rest at runtime.
- **[DECISION later]** Standalone build for outside contributors: needs the JEx libraries on a public Maven
  repository (for example GitHub Packages). Not required for the first release.

### 4.2 Module layout

```
JExLingo/
├── jexlingo-api/        public API, SPI, events, records (other plugins compile against this only)
├── jexlingo-common/     everything else: pipeline, chat, learning, persistence, commands, views, config
├── jexlingo-free/       entry point, paper-plugin.yml, plugin.yml, Free edition wiring
├── jexlingo-premium/    entry point, paper-plugin.yml, plugin.yml, Premium edition wiring
├── deploy/libretranslate/   docker-compose.yml, bench.sh, README
└── docs/                PLAN.md, ARCHITECTURE.md, CONFIG.md, PRIVACY.md
```

Gradle files copy JExVote 1:1 (shadow conventions, `dependenciesYml`, relocations, publishing), group
`de.jexcellence.lingo`, version `0.1.0`.

### 4.3 Editions
Pattern from JExVote: `sealed interface LingoEdition permits FreeEdition, PremiumEdition` in common; the entry point
picks the edition. Limits are enforced on start and on reload.

| Feature | Free | Premium |
|---|---|---|
| Chat translation, hover original, per-player settings | yes | yes |
| LibreTranslate provider (self-hosted or URL + key) | yes | yes |
| Languages | up to 3 **[DECISION]** | unlimited |
| Glossary (keep / force terms) | 50 entries | unlimited |
| Correction suggestions + staff review + translation memory | no | yes |
| Phrase cache promotion | no | yes |
| Private message translation (JExEssentials `/msg` hook) | no | yes |
| JExDiscord relay translation | no | yes |
| Training data export (TSV) | no | yes |
| Additional providers (Ollama, later others) | no | yes |
| Statistics view | basic `/lingo status` | GUI with history |

**[DECISION]** The split above is a proposal. For the owner's server the Premium edition is deployed.

---

## 5. Architecture

### 5.1 Packages (`jexlingo-common`, root `de.jexcellence.lingo`)

| Package | Contents |
|---|---|
| `.` | `JExLingo` (lifecycle, like `JExVote`), `LingoEdition`, `JExLingoApiImpl` |
| `config` | `LingoConfig` and section records: `ProviderSection`, `LanguageSection`, `ChatSection`, `LimitSection`, `CacheSection`, `LearningSection` |
| `provider` | `LibreTranslateProvider`, `LibreTranslateClient`, `ProviderHealthMonitor`, `CircuitBreaker` |
| `pipeline` | `TranslationPipeline`, stages (`SkipRuleStage`, `GlossaryStage`, `MemoryStage`, `CacheStage`, `ProviderStage`), `TextNormalizer`, `TokenMasker`, `InFlightRegistry` |
| `language` | `LanguageResolver`, `LanguageDetector`, `LanguageRegistry` |
| `chat` | `ChatTranslationListener`, `LingoChatRenderer`, `TranslatedMessageFactory`, `RecentMessageBuffer` |
| `learning` | `GlossaryService`, `TranslationMemoryService`, `SuggestionService`, `PhrasePromoter`, `TrainingExportService` |
| `database` | entities + repositories (section 8) |
| `command` | `LingoCommand`, `LingoAdminCommand` (JExCommand) |
| `view` | `LingoSettingsView`, `SuggestionReviewView`, `GlossaryView`, `StatusView` |
| `bedrock` | Cumulus form mirrors of every view |
| `placeholder` | PlaceholderAPI expansion |
| `integration` | JExEssentials (private messages), JExDiscord (relay), Floodgate |

Each class stays under 20 dependencies; the pipeline stages keep method complexity low by design.

### 5.2 API module (`de.jexcellence.lingo.api`)

| Type | Purpose |
|---|---|
| `LanguageCode` | record, validated ISO 639-1 code (`de`, `en`) |
| `TranslationRequest` | text, source (or `auto`), target, context (`CHAT`, `PRIVATE`, `DISCORD`, `API`) |
| `TranslationResult` | text, source, target, `TranslationOrigin` (`SAME_LANGUAGE`, `GLOSSARY`, `MEMORY`, `CACHE`, `PROVIDER`, `FALLBACK`), latency |
| `TranslationProvider` | SPI: `CompletableFuture<String> translate(...)`, `languages()`, `health()` |
| `JExLingoApi` | `translate(request)`, `languageOf(UUID)`, `setLanguage(UUID, LanguageCode)` |
| `ChatTranslatedEvent` | async, after translation; for logging/relay plugins |
| `PlayerLanguageChangeEvent` | language preference changed |
| `TranslationSuggestedEvent` | correction submitted (Premium) |

Registered through Bukkit `ServicesManager` and `platform.services()`.

### 5.3 Translation pipeline
Order for one text and one target language:

```
SkipRule -> Memory -> Phrase -> Cache -> Glossary + mask tokens -> InFlight -> Provider -> unmask -> result
```

1. **SkipRuleStage:** empty, only numbers/punctuation, shorter than `min-length`, starts with a skip prefix
   (default `!`), source equals target, sender opted out.
2. **TextNormalizer:** trim, collapse whitespace, lower-case key for lookups (the text itself keeps its case).
3. **TokenMasker:** URLs, online player names, `@mentions`, glossary KEEP terms, `{placeholders}` are replaced with
   tokens that LibreTranslate leaves unchanged, then restored. **[VERIFY in phase 2]** which token form survives
   Argos reliably (`_0_`, `{0}`, HTML mode with `format: html`). A spike with 200 test lines decides.
4. **GlossaryStage:** FORCE terms are replaced with their fixed translation after unmasking.
5. **MemoryStage (Premium):** exact match of normalized text + language pair in approved translation memory.
6. **CacheStage:** Caffeine, key = provider + source + target + normalized text, TTL and size from config.
7. **InFlightRegistry:** identical requests running at the same time share one future (spam does not multiply calls).
8. **ProviderStage:** concurrency limit (semaphore), circuit breaker, timeout. Failure -> `FALLBACK` with original.

Each stage returns `Optional<TranslationResult>`; the first hit wins. No stage blocks.

### 5.4 Threading
- `AsyncChatEvent` runs off the main thread. All futures use a dedicated bounded executor owned by JExLingo, shut
  down in `onDisable`.
- Database work runs only on that executor (JEHibernate async repositories), never on server or region threads.
- Folia: no Bukkit scheduler; `platform.scheduler()` for anything that touches players. `Audience#sendMessage` from
  async is safe on Paper **[VERIFY for Folia]**.
- Shutdown: pending futures complete with `FALLBACK`, executor shuts down with a 2 s grace period, interrupt flag is
  restored on `InterruptedException`.

---

## 6. Chat flow

### 6.1 Listener
`ChatTranslationListener` on `AsyncChatEvent`, priority `HIGHEST`, `ignoreCancelled = true`:
- Moderation plugins (mute, filter, anti-spam) run earlier and may cancel; JExLingo never sees cancelled messages.
- Collects target languages from `event.viewers()` (players only; console gets the original).
- Starts one pipeline future per target language that differs from the sender's language. Does not wait.
- Stores source text + futures in `RecentMessageBuffer` (in memory, 5 min, for hover and suggestions).
- Wraps the current renderer (6.2).

### 6.2 Renderer wrapping
`LingoChatRenderer` keeps a reference to the renderer that was set before it (JExEssentials format, or Paper's
default) and delegates:

```
render(source, displayName, message, viewer):
    if viewer is not a player, or viewer language == sender language -> previous.render(original)
    result = future(viewerLanguage) waited up to inline-wait, else FALLBACK
    previous.render(source, displayName, decorate(result), viewer)
```

- The server's chat format stays exactly as configured by JExEssentials. JExLingo only changes the message part.
- `decorate`: translation marker `[DE » EN]` (translation key) + translated text as `Component.text` + hover with
  the original + click `/lingo suggest <id>` (Premium).
- **[VERIFY in phase 3]** that Paper calls the renderer per viewer on the async chat thread, so a short wait there
  does not touch the main thread.

### 6.3 Timing modes (`chat.mode`)
- `INLINE` (default): wait up to `inline-wait` (default 400 ms, set from the benchmark) inside the renderer. One
  line, chat order kept. Timeout -> original, and the translation is not sent afterwards.
- `FOLLOW_UP`: original immediately, translated line sent as an extra line when ready. For slow hardware.

### 6.4 Bedrock (Floodgate)
Hover does not exist on Bedrock. Option `bedrock.show-original-line` (default on): translated line plus a second gray
line `Original: ...` for Bedrock viewers only. Suggestions on Bedrock go through `/lingo` form.

### 6.5 Edge cases
| Case | Behavior |
|---|---|
| Provider down / circuit open | Original shown, no marker; `/lingo status` shows the state |
| Message longer than `max-length` | Original shown |
| Player spams the same line | One provider call (InFlight + cache), per-player cooldown for provider calls |
| Translation equals the original | Shown without marker |
| Detected language already equals viewer language | No translation, no marker |
| Viewer opted out of incoming translation | Original |
| Sender opted out of outgoing translation | Original for everyone, text never sent to the provider |
| Mixed-language message | Detection confidence below threshold -> sender preference used |
| Reload during pending translations | Old pipeline finishes its futures, new requests use the new config |

---

## 7. Language handling

- `LanguageRegistry`: configured languages, validated against `/languages` of LibreTranslate on start. Missing
  model -> warning, language disabled, plugin keeps running.
- `LanguageResolver` per player: stored preference, else client locale (`player.locale().getLanguage()`), else
  `fallback-language` (default `en`). Locale outside the configured set -> fallback.
- Client locale changes (`PlayerLocaleChangeEvent`) update the cached value when the preference is `auto`.
- `LanguageDetector` (optional, `detection.enabled`, default off): LibreTranslate `/detect` for messages longer than
  `detection.min-length`; accepted only above `detection.min-confidence`. Off by default because it costs a second
  call per message; the benchmark decides whether it is affordable.
- First join: one short onboarding message with the detected language and the `/lingo` hint (stored flag, shown once).

---

## 8. Persistence (JEHibernate)

H2 file database by default, MariaDB/MySQL through `hibernate.properties` for networks. All new columns nullable
(ddl-auto cannot add NOT NULL to filled tables).

| Entity / table | Fields | Notes |
|---|---|---|
| `LingoPlayerSettings` / `lingo_player_settings` | `uuid` (PK), `language`, `translate_incoming`, `translate_outgoing`, `show_original`, `onboarded_at` | null = default from config |
| `GlossaryTerm` / `lingo_glossary` | `id`, `source_language`, `target_language` (null = all), `term`, `replacement` (null for KEEP), `mode` (`KEEP`, `FORCE`), `created_at` | loaded into memory on start and on change |
| `TranslationMemoryEntry` / `lingo_memory` (Premium) | `id`, `source_language`, `target_language`, `source_key`, `source_text`, `target_text`, `status` (`PENDING`, `APPROVED`, `REJECTED`), `submitted_by` (null after review), `hits`, `created_at`, `reviewed_at` | submitter UUID removed when reviewed |
| `PinnedPhrase` / `lingo_phrase` (Premium) | `id`, `source_language`, `target_language`, `source_key`, `target_text`, `uses`, `pinned_at` | no player reference |

Chat history is never stored. `RecentMessageBuffer` lives in memory only and expires after 5 minutes.

---

## 9. Learning and improving

The models do not learn by themselves. Improvement comes from layers in front of the model. Lookup order is the
pipeline order in 5.3.

### 9.1 Glossary (Free, limited)
- KEEP: terms never translated (`OneBlock`, `Island`, rank names, server name, `gg`).
- FORCE: fixed translation (`Insel` -> `Island` when that is the server's term).
- Managed with `/lingo glossary add|remove|list` and `GlossaryView`. Seeded with a default list in `glossary.yml`
  on first start.

### 9.2 Corrections and translation memory (Premium)
1. Viewer clicks the translated line -> `/lingo suggest <id>`; input through chat prompt (Java) or form (Bedrock).
2. Entry stored as `PENDING` with the source text from `RecentMessageBuffer`.
3. Staff open `SuggestionReviewView` (`jexlingo.review`): approve, edit, reject.
4. Approved entries are exact-match overrides from then on. Rejected entries are deleted after 7 days.
5. Abuse limits: suggestions per player per hour, minimum playtime, staff can ban a player from suggesting.

### 9.3 Phrase promotion (Premium)
Frequent short lines (`wie viel kostet das`, `anyone selling`) are counted in memory (Caffeine, hash only). When a
phrase reaches `learning.promote-after` uses in `learning.promote-window` and is at most `learning.promote-max-length`
characters, its translation is stored as `PinnedPhrase`. Pinned phrases survive restarts and skip the provider.

### 9.4 Quality signals
`/lingo status` and `StatusView`: share of results by origin (glossary, memory, cache, provider, fallback), p50/p95
latency, correction rate per language pair. A high correction rate points to missing glossary terms.

### 9.5 Model fine-tuning (later, offline)
`/lingo export` writes approved memory entries as TSV (source, target, language pair). Once there are several
thousand pairs, an Argos model can be retrained offline and loaded into LibreTranslate. Separate project, not part
of the plugin. The export format is fixed now so the data is usable later.

### 9.6 Local LLM provider (later, Premium)
`OllamaProvider` behind the same SPI, with glossary terms in the prompt. Better with slang and context, slower,
needs more memory. With 4-8 GB free it would replace LibreTranslate, not run next to it **[VERIFY with a benchmark
before building]**.

---

## 10. Configuration (JExConfig / ConfigMapper records)

Defaults live in code (record defaults); `config.yml` is generated once and never regenerated. Changes go through a
versioned migration with `.bak`.

```yaml
config-version: 1
provider:
  type: LIBRETRANSLATE
  url: "http://127.0.0.1:5000"
  api-key: ""
  connect-timeout-ms: 2000
  request-timeout-ms: 3000
  max-concurrent-requests: 8
  circuit-breaker:
    failure-threshold: 5
    open-duration-seconds: 30
languages:
  enabled: [de, en]
  fallback: en
detection:
  enabled: false
  min-length: 12
  min-confidence: 70
chat:
  mode: INLINE
  inline-wait-ms: 400
  min-length: 2
  max-length: 256
  skip-prefix: "!"
  player-cooldown-ms: 500
bedrock:
  show-original-line: true
cache:
  max-entries: 5000
  ttl-minutes: 60
learning:
  suggestions-per-hour: 5
  min-playtime-minutes: 30
  promote-after: 10
  promote-window-hours: 24
  promote-max-length: 40
```

Note: the empty `api-key` value conflicts with the suite rule "no empty YAML values". **[DECISION]** Either leave the
key out of the default file (record default `null` = no key) or read it from the `JEXLINGO_API_KEY` environment
variable. Proposal: both; the key is never written to the log.

---

## 11. Commands and permissions (JExCommand, command YAML)

| Command | Permission | Purpose |
|---|---|---|
| `/lingo` | `jexlingo.use` | opens `LingoSettingsView` (form on Bedrock) |
| `/lingo lang <code\|auto>` | `jexlingo.use` | set language |
| `/lingo incoming <enable\|disable>` | `jexlingo.use` | translate messages I read |
| `/lingo outgoing <enable\|disable>` | `jexlingo.use` | my messages may be translated |
| `/lingo original <enable\|disable>` | `jexlingo.use` | show the original line as well |
| `/lingo suggest <id>` | `jexlingo.suggest` | suggest a correction (Premium) |
| `/lingo review` | `jexlingo.review` | review queue (Premium) |
| `/lingo glossary add\|remove\|list` | `jexlingo.admin.glossary` | glossary |
| `/lingo test <from> <to> <text>` | `jexlingo.admin` | dry run with origin and latency |
| `/lingo status` | `jexlingo.admin` | provider health, breaker, cache, latency |
| `/lingo export` | `jexlingo.admin` | training TSV (Premium) |
| `/lingo reload` | `jexlingo.admin` | reload config, glossary, languages |

Subcommand values are words like `enable`/`disable` in command YAML, never `on/off` keys in translation files.
Command texts use the canonical `jexcommand.*` keys.

---

## 12. Text and GUI

- All text in `translations/en_US.yml` and `translations/de_DE.yml` (real umlauts), MiniMessage, `{placeholder}`
  values through `.with(...)`.
- Keys (draft): `lingo.chat.marker` (`<dark_gray>[<gray>{from} » {to}<dark_gray>]`), `lingo.chat.hover.original`,
  `lingo.chat.hover.suggest`, `lingo.chat.bedrock.original`, `lingo.onboarding.*`, `lingo.settings.*`,
  `lingo.status.*`, `lingo.glossary.*`, `lingo.suggest.*`, `lingo.review.*`, `lingo.error.*`.
- Symbols only from the approved set: `[OK] [X] [!] | > # -` and `» ● ○ ▸ ←`. No emoji.
- Views use the shared GUI kit: back 0, header 4, close 45, pages 48/50, `Label | Value` rows. Every view has a
  Cumulus form mirror.
- Translated chat lines must look like normal chat with a small, quiet marker, not like a system message.

---

## 13. Hosted option for other servers (later)

- Same plugin, `provider.url` = owner's public endpoint, `provider.api-key` = key issued per server.
- LibreTranslate built-in keys: `ltmanage keys add <req_per_min> --char-limit <n>`; abuse protection with
  `--req-flood-threshold`, hourly/daily limits.
- Endpoint behind Caddy with TLS; LibreTranslate only on `127.0.0.1`.
- Requirements before offering it: data processing agreement per server owner (Art. 28 GDPR), privacy notice, no
  request logging, capacity test with the benchmark.

---

## 14. Data protection (own server)

- Processed data: chat text (in memory only), player UUID with language settings, correction suggestions.
- Legal basis: Art. 6(1)(f) GDPR. Players can object (Art. 21) through `/lingo outgoing disable`; their text is then
  never sent to the provider.
- No chat history stored (Art. 5(1)(c)); submitter UUID removed after review; LibreTranslate local, no transfer.
- `docs/PRIVACY.md` with a text block for the server's privacy notice (Art. 13).

---

## 15. Testing

JUnit 5 + Mockito from the catalog. Pure logic only, no MockBukkit (catalog version targets 1.21).

| Test class | Covers |
|---|---|
| `TranslationPipelineTest` | stage order, first hit wins, fallback on failure |
| `TokenMaskerTest` | URLs, names, mentions, placeholders survive; nested and repeated tokens |
| `GlossaryStageTest` | KEEP and FORCE, case handling, language-scoped terms |
| `CircuitBreakerTest` | open after threshold, half-open probe, close on success |
| `InFlightRegistryTest` | identical concurrent requests share one future |
| `LibreTranslateClientTest` | JSON request/response, timeouts, 400/403/429/500, against JDK `HttpServer` fake |
| `LanguageResolverTest` | preference, locale, fallback, unsupported locale |
| `SkipRuleStageTest` | prefix, length, numbers only, opt-out |
| `LingoConfigTest` | defaults, invalid language codes, migration |
| `LingoEditionTest` | Free limits (languages, glossary size) |

Manual smoke test: `deploy/libretranslate/bench.sh` against the real container, and an in-game checklist
(`docs/TEST_CHECKLIST.md`): Java + Bedrock viewer, de/en/other locale, provider stopped mid-chat, reload.

---

## 16. Phases

| Phase | Content | Done when |
|---|---|---|
| 0 - Host | Docker on Debian, compose file, API key, benchmark, memory check, resolve all phase-0 [VERIFY] items | p50/p95 and RAM numbers recorded in `deploy/libretranslate/README.md` |
| 1 - Scaffold | Repo + submodule, 4 modules, Gradle from JExVote, entry points, editions, `paper-plugin.yml`, JExPlatform boot, config records, translation files, empty command | plugin enables on the test server and logs provider health |
| 2 - Provider + pipeline | `LibreTranslateClient`, circuit breaker, health monitor, pipeline stages, cache, in-flight, token spike, `/lingo test`, `/lingo status` | `/lingo test de en "..."` works, tests for these classes green |
| 3 - Chat | listener, renderer wrapping, INLINE/FOLLOW_UP, hover, Bedrock line, recent buffer, edge cases | de and en player chat correctly next to JExEssentials format |
| 4 - Players | `LingoPlayerSettings`, resolver, onboarding, settings view + form, all `/lingo` player commands, PAPI | settings persist across restart |
| 5 - Glossary | entity, service, `glossary.yml` seed, commands, view, Free limit | server terms stay intact |
| 6 - Learning (Premium) | suggestions, review view + form, memory stage, phrase promotion, export | approved correction is used on the next identical line |
| 7 - Integrations (Premium) | JExEssentials private messages, JExDiscord relay, `ChatTranslatedEvent` | `/msg` and Discord relay translate |
| 8 - Release prep | README, CONFIG.md, PRIVACY.md, CHANGELOG, Spigot/Modrinth page text, release-please entry, Sonar scan | first tagged release |
| Later | hosted option (13), Ollama provider (9.6), model fine-tuning (9.5), standalone build | separate decisions |

Each phase: compile the module only while working
(`./gradlew :JExLingo:jexlingo-common:compileJava --no-parallel --max-workers=1`); full build and tests once at the
end of the phase.

---

## 17. Git rules for this repo

- Author and committer: `JExcellence <justin.eiletz@outlook.com>`. No `Co-Authored-By` trailer, no generated-by
  lines in commits, PR descriptions or release notes.
- Optional `commit-msg` hook in the repo that removes `Co-Authored-By` lines.
- Commits inside the JExLingo submodule; the parent repo only gets the submodule pointer bump.
- Conventional commits (`feat(lingo): ...`, `fix(lingo): ...`) for release-please.

---

## 18. Open decisions

1. **[DECISION]** Free/Premium split in 4.3, especially the Free language limit (proposal: 3).
2. **[DECISION]** API key in config vs environment variable (section 10).
3. **[DECISION]** Default timing mode after the benchmark (`INLINE` expected).
4. **[DECISION]** Language detection on or off by default, after the benchmark.
5. **[DECISION]** License for the public repo (MIT, Apache-2.0, GPL-3.0).
6. **[DECISION]** Distribution: Spigot, Modrinth, Hangar, GitHub Releases.

---

## 19. Build status (2026-10-03)

Built in this session: phases 1-8 (code, resources, docs, 62 unit tests green). Open: phase 0 benchmark on the
Debian host (`deploy/libretranslate/bench.sh`), the in-game checklist (`docs/TEST_CHECKLIST.md`) and the
decisions in section 18.

Host (measured): Debian 13 trixie, 16 vCPU Skylake with AVX2, 30 GiB RAM (about 11 GiB free), no GPU, Docker 29.8,
Java 25, Minecraft on the same host. LibreTranslate therefore binds to `127.0.0.1` only.

### Differences from the plan

| Plan | Built | Why |
|---|---|---|
| Glossary before memory (5.3) | Memory and pinned phrases before glossary | an approved human correction must win over everything |
| `StatusView` GUI (5.1, 4.3) | `/lingo status` chat report for both editions | same information, no extra menu to maintain |
| Phase 7: JExEssentials `/msg` and JExDiscord relay | API + `ChatTranslatedEvent` only | neither plugin has a hook (no private-message event, no relay API); the hooks belong in those plugins |
| Suggestions via chat prompt | Click fills `/lingo suggest <id> `; Bedrock picks from the last 5 translated lines in a form | no chat capture needed, works on both platforms |
| Token format to be decided by a spike | `{n}` tokens, tolerant of spaces, line falls back to the original if a token is lost | still **[VERIFY]** with real Argos output in phase 0 |
| API key in config | `JEXLINGO_API_KEY` env var wins; config key commented out by default | no empty YAML value, secret stays out of files |
| Free/Premium split (4.3) | built as proposed: Free 3 languages, 50 glossary terms | decision 1 in section 18 still open; numbers live in `LingoEdition` |

### Decisions taken without owner input (change if wanted)

- Default glossary seed: `OneBlock, Ironman, gg, afk, brb, lol, xp, pvp, pve` keep, `Insel <-> Island` force.
- `INLINE` mode, 400 ms wait (to be tuned from the benchmark).
- Synchronous chat events (plugins calling `player.chat()` on the main thread) never wait inline; they use
  follow-up lines, so the main thread is never blocked.

---

## 20. 0.2.0 (2026-10-03)

Owner decisions: proprietary license, all rights reserved (LICENSE); repo stays public for viewing.

| Feature | Where |
|---|---|
| Chat slang expansion per source language, letter-spam shortening, shouting handled | `ChatTextPreparer`, `SlangDictionary`, `slang.yml` |
| Writing language separate from reading language | `PlayerLanguageSettings.writeLanguage`, `LanguageResolver.resolveWriting` |
| Writing-language learner (session sample, one-click offer, nothing stored) | `WritingLanguageLearner`, `LanguageVotes` |
| Incoming mode auto / click / off, `[T]` button, `/lingo show` | `IncomingMode`, `OnDemandTranslator` |
| Crowd approval of corrections (Premium) | `CrowdVotes`, `TranslationMemoryService.approveByVotes` |
| Right to erasure | `/lingo erase`, `PlayerSettingsService.erase`, `TranslationMemoryService.erasePending` |
| New config keys merged into live config with backup | `ConfigFileMerger`, `config-version: 2` |
| Normalized lookup keys (trailing `.`/`!`, letter spam) | `TextNormalizer.key` |

Still open: bench on the Debian host, in-game checklist, Free/Premium numbers, JExEssentials/JExDiscord hooks.

---

## 21. 0.3.0 (2026-10-03)

| Feature | Where |
|---|---|
| Daily statistics, aggregate only, batched writes, retention | `StatsRecorder`, `StatsService`, `DailyStatEntity` |
| Statistics menu with period filter (history Premium), Bedrock form, console report | `StatsView`, `StatsForm`, `StatsLines` |
| Staff tools: inspect, set, pause/resume, ping, cache clear | `LingoOpsHandler`, `TranslationSwitch` |
| Learning management: approved filter + revoke, phrases list/remove | `SuggestionReviewView`, `TranslationMemoryService.revoke`, `PhraseService.remove` |
| Class coupling under 20 | `LingoLearning`, `LingoOperations`, `LingoFiles`, `LingoStaffCommands` |

---

## 22. 0.4.0 - suite integrations (2026-10-03)

Phase 7 of the plan, done through the API so both plugins keep working without JExLingo.

| Plugin | What | Where |
|---|---|---|
| JExLingo | `translateFor`, `translateFrom`, `translateTo`, `writingLanguageOf` | `JExLingoApi`, `JExLingoApiImpl` |
| JExEssentials | `/msg` and `/r` translated for the receiver, original on hover | `ChatHandler`, `PrivateMessageTranslator` |
| JExDiscord | relay game -> Discord in `chat-relay.language`, Discord -> game per player | `ChatRelayListener`, `DiscordChatRelayListener`, `RelayTranslator` |

Each plugin checks for JExLingo on every call and only then loads its JExLingo bridge class
(`LingoPrivateMessageTranslator`, `LingoRelayTranslator`); both declare JExLingo as an optional dependency with
`join-classpath`. Fixed on the way: player and Discord text was inserted through `MessageBuilder.with(...)`, which
JExTranslate parses as MiniMessage, so `/msg`, `/me` and relayed Discord lines could carry click or hover markup.
