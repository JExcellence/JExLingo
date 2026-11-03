# LibreTranslate for JExLingo

JExLingo talks to a LibreTranslate instance over HTTP. LibreTranslate runs Argos Translate models locally; after
the first model download it needs no internet connection and sends chat text nowhere else.

The full walkthrough, from an empty server to translated chat, is in [docs/SELF_HOSTING.md](../../docs/SELF_HOSTING.md).

## Files

| File | Purpose |
|---|---|
| `docker-compose.yml` | LibreTranslate bound to `127.0.0.1`, models in a volume, API keys required |
| `.env.example` | Languages, port, memory and limits; copy to `.env` |
| `bench.sh` | Measures latency with typical chat lines |

## Reference measurement

LibreTranslate 1.9.6, English and German, 4 workers, 50 chat lines, on a 16 vCPU x86 server with AVX2:

| Value | Result |
|---|---|
| p50 | 145 ms |
| p95 | 217 ms |
| max | 947 ms (first request, models loading) |
| memory | 2.98 GiB |

`chat.inline-wait-ms: 400` in the plugin covers p95 with room to spare.

## Measure your own host

```bash
chmod +x bench.sh
./bench.sh http://127.0.0.1:5000 "$JEXLINGO_API_KEY" 5
```

Set `chat.inline-wait-ms` to about the reported p95 + 50 ms. If p95 is above 800 ms, use `chat.mode: FOLLOW_UP`.

## More languages

Add the code to `LT_LANGUAGES` in `.env` (for example `en,de,fr`), run `docker compose up -d`, then add it to
`languages.enabled` and run `/lingo reload`. `/lingo status` lists models that are still missing.

## License note

LibreTranslate is AGPL-3.0. Running it unchanged is fine. If you change LibreTranslate itself and offer it as a
network service, you must publish those changes. JExLingo only talks to it over HTTP and is not affected.
