# Changelog

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
