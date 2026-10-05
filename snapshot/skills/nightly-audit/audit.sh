#!/bin/sh
# nightly-audit: print one line per failing repo. Silence = all clear.
# Read-only: never stages, commits, pushes, pulls, or fixes anything.
# Used as a cron trigger_cmd: empty output short-circuits the run (0 tokens),
# so the agent wakes only when something is actually wrong.
LIST="${AUDIT_LIST:-$HOME/.opencrabs/state/nightly-audit/repos.txt}"

if [ ! -f "$LIST" ]; then
  mkdir -p "$(dirname "$LIST")" 2>/dev/null
  {
    echo '# nightly-audit repo list — one repo path per line, # comments ok'
    echo "$HOME/.opencrabs/src"
    echo "$HOME/.opencrabs/src-crab"
    echo "$HOME/bankai"
  } > "$LIST"
fi

while IFS= read -r r || [ -n "$r" ]; do
  case "$r" in ''|\#*) continue ;; esac
  if ! git -C "$r" rev-parse --git-dir >/dev/null 2>&1; then
    echo "MISSING: $r is not a git repo"
    continue
  fi
  if git -C "$r" remote 2>/dev/null | grep -q .; then
    if ! git -C "$r" fetch --quiet 2>/dev/null; then
      echo "FETCH-FAIL: $r (no network or bad remote)"
    fi
  fi
  d=$(git -C "$r" status --porcelain 2>/dev/null | wc -l | tr -d ' ')
  if [ "${d:-0}" -gt 0 ] 2>/dev/null; then
    echo "DIRTY($d): $r has uncommitted changes"
  fi
  b=$(git -C "$r" rev-list --count '@{u}..HEAD' 2>/dev/null)
  if [ -n "$b" ] && [ "$b" -gt 0 ] 2>/dev/null; then
    echo "UNPUSHED($b): $r has local commits not on its upstream"
  fi
  h=$(git -C "$r" rev-list --count 'HEAD..@{u}' 2>/dev/null)
  if [ -n "$h" ] && [ "$h" -gt 0 ] 2>/dev/null; then
    echo "BEHIND($h): $r is behind its upstream — pull before building on it"
  fi
done < "$LIST"
