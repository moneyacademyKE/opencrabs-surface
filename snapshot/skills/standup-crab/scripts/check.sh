#!/bin/bash
# standup-crab trigger — quiet-day pre-filter.
# Prints a signal line per repo with commits in the last 24h, or if a listed
# repo path is missing. Silent on a nothing-happened day.
# Read-only: never writes state.
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
REPOS="${CRAB_REPOS:-$HOME/.opencrabs/state/standup-crab/repos.txt}"
[ -f "$REPOS" ] || exit 0
while IFS= read -r repo; do
  case "$repo" in \#*|"") continue ;; esac
  if [ ! -d "$repo/.git" ] && [ ! -f "$repo/.git" ]; then
    echo "MISSING-REPO $repo — listed but not on disk"
    continue
  fi
  n=$(git -C "$repo" log --since="24 hours ago" --all --oneline 2>/dev/null | wc -l | xargs)
  [ "$n" -gt 0 ] 2>/dev/null && echo "COMMITS $repo: $n in last 24h"
done < "$REPOS"
exit 0
