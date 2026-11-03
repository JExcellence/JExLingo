# Self-hosting JExLingo

From an empty server to translated chat. JExLingo translates through your own LibreTranslate instance; after the
first model download nothing leaves your machine.

## What you need

| | |
|---|---|
| Minecraft | Paper 26.x (Folia works too), Java 25 |
| Translator | Docker with Compose, on the same machine or reachable over a private network |
| Memory | 2.98 GiB measured for English and German with 4 LibreTranslate workers; plan roughly 1.5 GiB more per further language (estimate, check with `docker stats`) |
| Disk | 1.02 GB for the image plus 330 MB for the English and German models (measured) |
| CPU | an x86 CPU with AVX2; no GPU needed. The reference host answered a chat line in 145 ms (median), 217 ms (p95) |

JExLingo itself needs no database setup: it creates an embedded H2 file in `plugins/JExLingo/database/`. A MySQL or
MariaDB server is optional, see `plugins/JExLingo/database/hibernate.properties`.

## 1. Start LibreTranslate

```bash
mkdir -p ~/jexlingo-lt && cd ~/jexlingo-lt
# copy docker-compose.yml and .env.example from deploy/libretranslate in this repository
cp .env.example .env
nano .env            # LT_LANGUAGES=en,de,fr ... the languages your players use
docker compose up -d
docker compose logs -f   # the first start downloads the models; wait for "Booting worker"
```

Check it answers:

```bash
curl -s -X POST 127.0.0.1:5000/translate -H 'Content-Type: application/json' \
  -d '{"q":"Hallo Welt","source":"de","target":"en","format":"text"}'
```

## 2. Create an API key

The compose file starts LibreTranslate with `--api-keys`, so every request needs a key:

```bash
docker exec jexlingo-libretranslate ltmanage keys add 600
```

Give the key to the Minecraft server process (preferred, it never lands in a file):

```bash
export JEXLINGO_API_KEY=<the key>
```

or set `provider.api-key` in `plugins/JExLingo/config.yml`. The key is never written to the log.

## 3. Install the plugin

1. Put `JExLingo-<version>-Free.jar` or `-Premium.jar` into `plugins/` and start the server once.
2. Open `plugins/JExLingo/config.yml`:
   - `provider.url`: `http://127.0.0.1:5000` (or the `LT_PORT` you chose)
   - `languages.enabled`: the same codes as `LT_LANGUAGES`
   - `languages.fallback`: the language for players whose game language is not enabled
3. Run `/lingo reload`.

## 4. Check it works

| Command | Expected |
|---|---|
| `/lingo status` | Reachable: online, no "Missing models" row |
| `/lingo test de en Hat jemand Eisen zu verkaufen?` | `Does anyone have iron to sell?` |
| Two players with different game languages chat | each reads the other in their language, original on hover |

## Another machine

Do not open port 5000 to the internet. Either connect the two machines over WireGuard or Tailscale and use the
private address in `provider.url`, or put Caddy with TLS in front of LibreTranslate and keep the API key.

## Log messages and what to do

| Log line | Do this |
|---|---|
| `... at http://... is not reachable (connection refused ...)` | Start LibreTranslate, or correct `provider.url` |
| `The translation provider has no model for fr. Start LibreTranslate with --load-only de,en,fr ...` | Set `LT_LANGUAGES` to the printed value, `docker compose up -d` |
| `The translation provider rejected the API key (403) ...` | Create a key (step 2) and set it, then `/lingo reload` |
| Lines stay untranslated, no warning | See "When a line stays untranslated" in the README |

## Updating

```bash
cd ~/jexlingo-lt && docker compose pull && docker compose up -d
```

For the plugin, replace the jar and restart. New config options are added to your `config.yml` automatically; a
backup of the old file is kept next to it.

## Licenses

LibreTranslate is AGPL-3.0. Running it unchanged is fine; if you change LibreTranslate itself and offer it as a
network service, you must publish those changes. JExLingo talks to it over HTTP only.
