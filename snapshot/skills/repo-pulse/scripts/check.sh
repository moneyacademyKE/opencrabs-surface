#!/bin/bash
# repo-pulse trigger — quiet-day pre-filter.
# Prints a signal line per repo with new issues/PRs/releases/failed CI since
# the watermark in since.txt (edition owns bumping it). Silent when quiet.
# Read-only: never writes state, never mutates the watermark.
export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
STATE="${CRAB_STATE:-$HOME/.opencrabs/state/repo-pulse}"
REPOS="$STATE/repos.txt"
SINCE_FILE="$STATE/since.txt"
if [ -s "$SINCE_FILE" ]; then SINCE="$(cat "$SINCE_FILE")"; else SINCE="$(date -u -v-24H +%Y-%m-%dT%H:%M:%SZ)"; fi
[ -f "$REPOS" ] || exit 0
while IFS= read -r repo; do
  case "$repo" in \#*|"") continue ;; esac
  n=$(gh api "repos/$repo/issues?state=all&sort=updated&direction=desc&per_page=15" \
      --jq "[.[] | select(.updated_at > \"$SINCE\")] | length" 2>/dev/null) || { echo "GH-FAIL $repo — woke for judgment"; continue; }
  [ "$n" -gt 0 ] 2>/dev/null && echo "ACTIVITY $repo: $n issue(s)/PR(s) updated since $SINCE"
  rel=$(gh api "repos/$repo/releases?per_page=5" \
      --jq "[.[] | select(.published_at > \"$SINCE\")] | length" 2>/dev/null)
  [ "$rel" -gt 0 ] 2>/dev/null && echo "RELEASE $repo: $rel new since $SINCE"
  fail=$(gh api "repos/$repo/actions/runs?per_page=10" \
      --jq "[.workflow_runs[] | select(.created_at > \"$SINCE\" and .conclusion == \"failure\")] | length" 2>/dev/null)
  [ "$fail" -gt 0 ] 2>/dev/null && echo "RED-CI $repo: $fail failed run(s) since $SINCE"
done < "$REPOS"
exit 0
