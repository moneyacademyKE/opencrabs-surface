---
name: standup-crab
description: Daily internal standup draft - last-24h commits across active repos plus the in-flight ledger, distilled to Shipped / In flight / Blockers.
---
# standup-crab

A draft for the owner to read aloud, not a report to act on.

## State

- `~/.opencrabs/state/standup-crab/repos.txt` - absolute repo paths, one per line, `#` skipped.

## Run

1. For each path: `git -C <path> log --since="24 hours" --oneline -10` and `git -C <path> status -s | head -3`. Missing path: note it in one line, move on.
2. Skim the last 30 lines of `~/.opencrabs/state/inflight.md` if it exists.
3. Output, max 8 lines total:
   - **Shipped** - commit subjects (first line only, repo in parens)
   - **In flight** - from inflight entries plus dirty working trees
   - **Blockers** - `none`, or the one thing actually blocking
4. Empty day: `standup-crab: nothing shipped in 24h; inflight has N items.`

## Rules

- Read-only git. Never fetch, pull, push, or commit.
- Commit subjects are quoted as-is - this is a mirror of the tree, not a rewrite of history.
