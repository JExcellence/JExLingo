# JExLingo in-game test checklist

> **Status:** console checks of the "Unreleased" section run on a clean Paper 26.3 server (2026-11-03); the client
> checks are open. Run on the test server before every release.

Setup: LibreTranslate running ([docs/SELF_HOSTING.md](SELF_HOSTING.md)), one Java client with German game language,
one with English, one Bedrock client through Geyser if available. The integration section needs the matching plugins;
everything else runs with JExLingo alone.

## Unreleased: self-hosting and upgrades

- [x] Clean server with only the JExLingo jar: starts, downloads its libraries, creates the H2 database, no SLF4J
  lines in the console.
- [x] LibreTranslate stopped: the log names `provider.url`, says "connection refused" and links the guide.
- [x] `provider.url` changed to the running instance + `/lingo reload`: log says "reachable".
- [ ] `languages.enabled` with a language LibreTranslate did not load: the log prints the exact `--load-only` value.
- [ ] LibreTranslate with `--api-keys` and no key set: the log says the key was rejected, once.
- [ ] `provider.url: http://<public address>`: a warning about unencrypted chat at start.
- [x] Upgrade over an older install: `config.yml`, `commands/lingo.yml` and the translation files get the new keys,
  each with a `.bak-` file; the stats tables get their new columns.

## Unreleased: translation quality

- [x] `/lingo test de en Hat jemand Notch gesehen?` keeps `Notch`.
- [x] A name with an underscore (`Steve_42`) is kept even when that player is offline.
- [ ] `&aHallo zusammen` from a player with colour permission arrives as `&aHello everyone` in green.
- [x] `HILFE` then `hilfe`: the second line is not in capitals.
- [ ] A player called `Help` is online: `can someone help me` translates fully; `Help, wo bist du?` keeps the name.
- [ ] `chat.protect-names: ALWAYS` + reload: `help` is kept as a name; `MENTIONS`: only `@Help` is kept.
- [ ] A player sends four lines within a second: the first three are translated (`chat.burst: 3`).
- [ ] `/lingo status` shows the "Broken tokens / saved" row.

## Unreleased: learning and glossary (Premium where noted)

- [x] `/lingo glossary import` with nothing new: "Added 0 terms ... 11 were already there".
- [x] Add a term to `glossary.yml`, `/lingo glossary import`: it is added, existing terms stay.
- [x] `/lingo glossary add force de>en:Truhe chest`: `Truhe` becomes `chest` from German to English only.
- [ ] Premium: after three approved corrections that keep `Spawn`, `/lingo terms` proposes it; a click fills in the
  command.
- [ ] Premium: after three approved corrections that change `box` to `chest` in lines with `Truhe`, `/lingo terms`
  proposes `Truhe » chest` for DE » EN.
- [ ] Premium: a pinned phrase is dropped after a glossary edit and after two players suggested a change to it.
- [ ] Premium: `/lingo stats` shows "corrections per 1,000" next to a pair that got corrections.

## Provider

- [ ] `/lingo status`: provider reachable, breaker `closed`, no missing models.
- [ ] `/lingo test de en Hat jemand Eisen?` returns an English line with origin `provider`; repeat shows `cache`.
- [ ] Stop the container (`docker stop jexlingo-libretranslate`), chat: originals appear without delay, log warns once.
- [ ] After 5 failures `/lingo status` shows breaker `open`; start the container, the next probe closes it.

## Chat

- [ ] German player writes German: English player sees `[DE » EN]` + translation in the server's chat format
  (rank, name, colours intact), German player sees the original.
- [ ] Hover shows `Original (DE)` and the original text.
- [ ] `!test` is not translated; `123` is not translated.
- [ ] A line with a URL, `@Name`, `[item]` and a player name keeps them unchanged.
- [ ] Muted player: nothing is translated or shown.
- [ ] `/lingo outgoing disable`: the player's lines reach nobody translated.
- [ ] `/lingo incoming off`: the player sees every line as written.
- [ ] `/lingo original enable`: the original shows as a second line.
- [ ] `chat.mode: FOLLOW_UP` + reload: original first, translated line right after.
- [ ] A chat line containing `<red>hi</red>` or `<click:run_command:/op me>` shows as literal text in the translation.

## Suite integrations (only with the matching plugins installed)

- [ ] JExEssentials: German player `/msg <english player> Hast du Eisen?`: receiver sees `[DE » EN]` and English,
  hover shows the original; sender and social spy see the original.
- [ ] Receiver with `/lingo incoming off` or sender with `/lingo outgoing disable`: message arrives as written.
- [ ] `/lingo pause`: private messages arrive as written.
- [ ] A private message with `<click:run_command:/op me>` arrives as plain text (also without JExLingo).
- [ ] Without JExLingo installed: `/msg` and `/r` work exactly as before.
- [ ] JExDiscord with `chat-relay.language: en`: a German game line appears in Discord in English with the original
  as small text below.
- [ ] JExDiscord with `message-content-intent: true`: a Discord line appears in game in each player's language with
  `[EN » DE]` and the original on hover; console shows the original.

## 0.3.0 features

- [ ] `/lingo stats`: header shows provider online and latency; overview counts grow while players chat.
- [ ] Premium: the filter switches today / 7 days / 30 days; Free shows the locked history card.
- [ ] Restart: today's count in `%jexlingo_stats_today%` continues from the stored value.
- [ ] `/lingo stats week` in the console prints the chat version.
- [ ] `/lingo inspect <player>` shows chosen and effective languages, also for an offline player.
- [ ] `/lingo set incoming <player> click` changes their mode; they see `[T]` buttons.
- [ ] `/lingo pause 1`: lines stay untranslated for one minute, then translation resumes by itself.
- [ ] `/lingo ping` reports the provider time; with LibreTranslate stopped it reports the error.
- [ ] Premium: review menu filter shows approved corrections; right-click takes one back; the line is translated by
  the provider again.
- [ ] Premium: `/lingo phrases` lists pinned phrases; `/lingo phrases remove <id>` unpins one.

## 0.2.0 features

- [ ] `idk tbh` from an English player and `vllt morgen` from a German player translate as full words.
- [ ] `HILF MIR BITTE` arrives as `HELP ME PLEASE` (capitals kept).
- [ ] Player with English client writes German 5 times: gets the `[!] You seem to write in German` hint; clicking it
  sets the writing language; their lines are now translated from German.
- [ ] `/lingo incoming click`: foreign lines show `[T]`; clicking it sends the translated line; own-language lines
  have no button.
- [ ] Premium: three players suggest the same correction: the third gets "used from now on", the line now shows it.
- [ ] `/lingo erase <player>` removes their settings (menu shows defaults after relog) and their open suggestions.
- [ ] Existing 0.1.0 `config.yml`: after start it contains the new keys with comments, a `.bak-` file exists.

## Settings and menus

- [ ] `/lingo` opens the menu; each card cycles with left/right click; values survive a relog.
- [ ] `/lingo lang auto|de|en` and the language card agree.
- [ ] First join of a new player shows the onboarding hint once.
- [ ] Bedrock: `/lingo` opens the form; saving applies; translated lines show the original as second line.

## Glossary

- [ ] `/lingo glossary add keep OneBlock` then `Meine OneBlock Insel` keeps `OneBlock`.
- [ ] `/lingo glossary add force Insel Island` then `Insel` alone becomes `Island` without a provider call (origin
  `glossary` in `/lingo test`).
- [ ] Glossary menu lists terms; right-click removes one.
- [ ] Free edition: the 51st term is refused.

## Premium learning

- [ ] Click a translated line: chat box shows `/lingo suggest <id> `; send a correction, see `[OK]`.
- [ ] `/lingo review` lists it; left-click approves; the same line now shows the approved text (origin `memory`).
- [ ] Right-click rejects; `/lingo review block <player>` stops further suggestions.
- [ ] Bedrock: `/lingo suggest` lists recent translated lines and opens the input form.
- [ ] `/lingo export` writes `exports/jexlingo-memory-*.tsv`.

## Reload and restart

- [ ] Change `languages.enabled`, `/lingo reload`, `/lingo status` reflects it.
- [ ] Restart: settings, glossary, approved entries and pinned phrases are still there.
