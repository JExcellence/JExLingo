# LibreTranslate for JExLingo (Debian)

> **Status:** ready to run, latency not yet measured. **Last verified:** 2026-10-03.

JExLingo talks to a LibreTranslate instance over HTTP. LibreTranslate runs Argos Translate models locally; after
the first model download it needs no internet connection and sends chat text nowhere else.

## Owner host (measured 2026-10-03)

| Item | Value |
|---|---|
| OS | Debian 13 (trixie), kernel 6.12 cloud |
| CPU | Intel Xeon (Skylake), 16 vCPU, AVX2 yes |
| RAM | 30 GiB total, about 11 GiB available |
| GPU | none |
| Docker | 29.8.0, Compose v5.1.4 |
| Java | OpenJDK 25.0.3 |
| Minecraft | same host, port 25565; port 5000 free |

Fits easily: en+de needs an estimated 1-2.5 GB **[VERIFY with `bench.sh`]**. The container is capped at 3 GB.

## Start

```bash
mkdir -p ~/jexlingo-lt && cd ~/jexlingo-lt
# copy docker-compose.yml and bench.sh from this folder
docker compose up -d
docker compose logs -f   # first start downloads the en/de models, wait for "Running on"
```

## API key

`--api-keys` makes LibreTranslate check keys. Create one for the plugin (600 requests per minute):

```bash
docker exec jexlingo-libretranslate ltmanage keys add 600
```

Give it to the plugin through the environment of the Minecraft server process (preferred):

```bash
export JEXLINGO_API_KEY=<the key>
```

or in `plugins/JExLingo/config.yml` as `provider.api-key`. The key never appears in logs.

## Measure

```bash
chmod +x bench.sh
./bench.sh http://127.0.0.1:5000 "$JEXLINGO_API_KEY" 5
```

Set `chat.inline-wait-ms` to about the reported p95 + 50 ms. If p95 is above 800 ms, use `chat.mode: FOLLOW_UP`.
Record the numbers in the table above.

## More languages

Add the code to `--load-only` (for example `en,de,fr`), `docker compose up -d`, then add it to
`languages.enabled` and run `/lingo reload`. `/lingo status` lists models that are still missing.

## Other hosts

If the Minecraft server runs on another machine, do not open port 5000. Use WireGuard/Tailscale, or Caddy with TLS
in front of LibreTranslate plus an API key, and allow only that port in `ufw`.

## License note

LibreTranslate is AGPL-3.0. Running it unchanged is fine. If you change LibreTranslate itself and offer it as a
network service, you must publish those changes. JExLingo only talks to it over HTTP and is not affected.
