#!/usr/bin/env bash
# Measures LibreTranslate latency with typical chat lines, so the plugin defaults (chat.inline-wait-ms,
# provider.max-concurrent-requests) come from real numbers.
#
# Usage: ./bench.sh [url] [api-key] [rounds]
#   url     default http://127.0.0.1:5000
#   api-key default $JEXLINGO_API_KEY
#   rounds  default 5 (each round sends every line in both directions)
set -euo pipefail

URL="${1:-http://127.0.0.1:5000}"
KEY="${2:-${JEXLINGO_API_KEY:-}}"
ROUNDS="${3:-5}"

LINES_DE=(
  "Hat jemand Eisen zu verkaufen?"
  "Wie komme ich zur nächsten Phase?"
  "Danke für die Hilfe, gg"
  "Wer will mit mir auf meine Insel?"
  "Kann mir jemand erklären, wie die Minions funktionieren?"
  "Ich bin gleich zurück"
  "Wo finde ich den Shop?"
  "Das Event fängt in fünf Minuten an"
)
LINES_EN=(
  "Anyone selling iron?"
  "How do I get to the next phase?"
  "Thanks for the help, gg"
  "Who wants to join my island?"
  "Can someone explain how minions work?"
  "Be right back"
  "Where is the shop?"
  "The event starts in five minutes"
)

command -v curl >/dev/null || { echo "curl is required"; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required"; exit 1; }

times_file="$(mktemp)"
trap 'rm -f "$times_file"' EXIT

translate() {
  local text="$1" source="$2" target="$3"
  local body
  body=$(python3 -c 'import json,sys; d={"q":sys.argv[1],"source":sys.argv[2],"target":sys.argv[3],"format":"text"}; k=sys.argv[4]; d.update({"api_key":k} if k else {}); print(json.dumps(d))' \
    "$text" "$source" "$target" "$KEY")
  curl -s -o /dev/null -w '%{time_total}\n' -H 'Content-Type: application/json' -d "$body" "$URL/translate" >> "$times_file"
}

echo "Warming up the models..."
translate "Hallo" de en
translate "Hello" en de
: > "$times_file"

echo "Sending $(( ${#LINES_DE[@]} * 2 * ROUNDS )) requests to $URL ..."
for _ in $(seq 1 "$ROUNDS"); do
  for line in "${LINES_DE[@]}"; do translate "$line" de en; done
  for line in "${LINES_EN[@]}"; do translate "$line" en de; done
done

python3 - "$times_file" <<'PY'
import sys
values = sorted(float(v) * 1000 for v in open(sys.argv[1]) if v.strip())
def pct(p):
    rank = max(1, -(-len(values) * p // 100))
    return values[int(rank) - 1]
print(f"requests {len(values)}  p50 {pct(50):.0f} ms  p95 {pct(95):.0f} ms  max {values[-1]:.0f} ms")
print("Suggested chat.inline-wait-ms: about p95 + 50 ms, at most 800.")
PY

if command -v docker >/dev/null; then
  docker stats --no-stream --format 'container {{.Name}}  memory {{.MemUsage}}  cpu {{.CPUPerc}}' jexlingo-libretranslate || true
fi
