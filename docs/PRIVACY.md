# JExLingo and data protection

> **Status:** matches 0.3.0. **Last verified:** 2026-10-03. Not legal advice; adapt it to your server.

## What is processed

| Data | Where | How long |
|---|---|---|
| Chat line text | memory, sent to the configured LibreTranslate URL | translation cache up to `cache.ttl-minutes`; recent-message buffer 5 minutes |
| Player UUID + translation settings | database `jexlingo_player_settings` | until deleted |
| Suggested translations (Premium) | database `jexlingo_memory`; submitter UUID only while pending | approved: kept; rejected: deleted after 7 days |
| Pinned phrases (Premium) | database `jexlingo_phrase`, no player reference | kept |
| Phrase counters (Premium) | memory, SHA-256 hashes only | `learning.promote-window-hours` |
| Writing-language samples | memory, detected language codes only | until the player leaves |
| Daily statistics | database `jexlingo_daily_stats`: counts per day, language pair and source, no text, no player | `statistics.retention-days` |

No chat history is stored. Logs never contain chat text or the API key.

## Erasure

`/lingo erase <player>` deletes the player's stored settings and their open suggestions (Art. 17 GDPR). Approved
corrections and pinned phrases hold no player reference and stay.

## Self-hosted (default)

With LibreTranslate on your own machine (`127.0.0.1`), no data leaves your server. Legal basis is usually
Art. 6(1)(f) GDPR (legitimate interest in a multilingual chat). Players can object (Art. 21) with
`/lingo outgoing disable`; their messages are then never sent to the translator.

## Remote or shared instance

If `provider.url` points to a machine run by someone else, that operator processes your players' chat on your
behalf: you need a data processing agreement (Art. 28 GDPR), and the operator must not log request contents.

## Text block for your privacy notice

> Our chat can be translated automatically. When you send a chat message, its text is processed by a translation
> service we run ourselves and shown to other players in their language. Chat messages are not stored; translations
> are kept in memory for at most one hour. We store your translation settings (language, on/off switches) with your
> player ID. You can stop your messages from being translated at any time with `/lingo outgoing disable`.
