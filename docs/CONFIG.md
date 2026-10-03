# JExLingo configuration

> **Status:** matches 0.1.0. **Last verified:** 2026-10-03. **Related:** [../README.md](../README.md),
> [PLAN.md](PLAN.md).

`plugins/JExLingo/config.yml` is written once and never overwritten. Defaults live in the plugin
(`LingoConfigLoader`): a missing key uses its default, an out-of-range number is clamped, an invalid value falls back
and a warning naming the key is logged. `/lingo reload` applies most changes; the database, cache size and phrase
window need a restart.

## Environment variables

| Variable | Overrides | Use |
|---|---|---|
| `JEXLINGO_PROVIDER_URL` | `provider.url` | deployment-specific URL |
| `JEXLINGO_API_KEY` | `provider.api-key` | keep the key out of the file |

## Keys

| Key | Default | Range | Meaning |
|---|---|---|---|
| `provider.type` | `libretranslate` | | backend id; Premium may select providers registered through the API |
| `provider.url` | `http://127.0.0.1:5000` | http(s) URL | LibreTranslate base URL |
| `provider.api-key` | unset | | LibreTranslate key |
| `provider.connect-timeout-ms` | 2000 | 100-60000 | TCP connect timeout |
| `provider.request-timeout-ms` | 3000 | 100-60000 | timeout per request |
| `provider.max-concurrent-requests` | 8 | 1-256 | requests in flight; more are shown untranslated |
| `provider.health-check-seconds` | 60 | 10-3600 | language list check interval |
| `provider.circuit-breaker.failure-threshold` | 5 | 1-100 | failures in a row that pause the provider |
| `provider.circuit-breaker.open-duration-seconds` | 30 | 1-3600 | pause before one probe request |
| `languages.enabled` | `[de, en]` | ISO codes | languages translated between; Free uses the first 3 |
| `languages.fallback` | `en` | enabled code | language for unsupported game languages |
| `detection.enabled` | `false` | | detect the written language per message (second provider call) |
| `detection.min-length` | 12 | 1-500 | shortest message that is checked |
| `detection.min-confidence` | 70 | 0-100 | lowest confidence that overrides the sender's language |
| `chat.mode` | `INLINE` | `INLINE`, `FOLLOW_UP` | one translated line, or original first and translation after |
| `chat.inline-wait-ms` | 400 | 0-5000 | wait budget per message in `INLINE` mode |
| `chat.min-length` | 2 | 1-256 | shortest translated message |
| `chat.max-length` | 256 | min-2000 | longest translated message |
| `chat.skip-prefix` | `!` | | messages starting with it stay untranslated; `""` disables |
| `chat.player-cooldown-ms` | 500 | 0-60000 | minimum time between provider calls per sender |
| `chat.onboarding` | `true` | | one-time hint on first join |
| `bedrock.show-original-line` | `true` | | Bedrock viewers get the original as second line |
| `cache.max-entries` | 5000 | 0-1000000 | cached provider results (memory only) |
| `cache.ttl-minutes` | 60 | 1-10080 | lifetime of a cache entry |
| `learning.suggestions-per-hour` | 5 | 0-1000 | Premium: suggestions per player per hour |
| `learning.min-playtime-minutes` | 30 | 0-100000 | Premium: playtime needed to suggest |
| `learning.promote-after` | 10 | 2-10000 | Premium: uses within the window that pin a phrase |
| `learning.promote-window-hours` | 24 | 1-720 | Premium: counting window |
| `learning.promote-max-length` | 40 | 1-256 | Premium: longest phrase that can be pinned |

## Other files

| File | Purpose |
|---|---|
| `glossary.yml` | default terms, read once while the glossary table is empty |
| `commands/lingo.yml` | command tree: names, aliases, permissions, defaults |
| `database/hibernate.properties` | H2 by default; MySQL, MariaDB, PostgreSQL supported |
| `translations/en_US.yml`, `de_DE.yml` | every text; new keys from updates are merged in, your wording is kept |
| `exports/` | Premium TSV exports |
