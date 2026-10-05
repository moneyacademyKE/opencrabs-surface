#!/bin/bash
# competitor-watch trigger — quiet-day pre-filter.
# Prints a signal line per page that changed since the last trigger run
# (or failed to fetch). Prints NOTHING on a quiet day. Maintains its own
# raw-fetch baselines; never touches the edition's snapshots/ or alerts.txt.
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
STATE="${CRAB_STATE:-$HOME/.opencrabs/state/competitor-watch}"
URLS="$STATE/urls.txt"
BASE="$STATE/trigger-baselines.txt"
UA="Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36"
touch "$BASE"
while IFS= read -r line; do
  case "$line" in \#*|"") continue ;; esac
  url="${line%%|*}"; url="$(echo "$url" | xargs)"
  name="${line##*|}"; name="$(echo "$name" | xargs)"
  [ -n "$name" ] && [ "$name" != "$url" ] || name="$(printf '%s' "$url" | cksum | cut -d' ' -f1)"
  if [[ "$url" == *"/releases"* ]]; then
    # tag-set only: immune to nav-counter chrome (stars/forks/issues drift daily)
    cur="$(curl -fsSL --max-time 25 -A "$UA" "$url" 2>/dev/null | grep -o 'releases/tag/[^"?]*' | sort -u | cksum | cut -d' ' -f1)"
  else
    cur="$(curl -fsSL --max-time 25 -A "$UA" "$url" 2>/dev/null | cksum | cut -d' ' -f1)"
  fi
  if [ -z "$cur" ]; then
    echo "FETCH-FAIL $name ($url) — woke for judgment"
    continue
  fi
  prev="$(awk -F' ' -v n="$name" '$1==n {print $2}' "$BASE")"
  if [ -z "$prev" ]; then
    printf '%s %s\n' "$name" "$cur" >> "$BASE"   # silent bootstrap
  elif [ "$prev" != "$cur" ]; then
    echo "CHANGED $name ($url)"
    awk -F' ' -v OFS=' ' -v n="$name" -v c="$cur" '$1==n {$2=c} {print}' "$BASE" > "$BASE.tmp" && mv "$BASE.tmp" "$BASE"
  fi
done < "$URLS"
exit 0
