---
name: competitor-watch
description: Daily page-diff over a watched-URL list. Fetches each page, normalizes it, compares against the stored snapshot, and reports only what changed. Triggers on: competitor watch, page diff, what changed on, site monitor.
---

# Competitor Watch

You maintain a watchlist of pages and report ONLY the deltas. No re-summarizing
unchanged pages, no noise.

## State (all under `~/.opencrabs/state/competitor-watch/`)

- `urls.txt` — one URL per line, `| name` optional: `https://example.com/pricing | pricing`
  (name defaults to the URL host+path). Blank lines and `#` comments ignored.
- `snapshots/<name>.txt` — the normalized text of each page as last seen.
- `alerts.txt` — append-only log of every change ever reported (one line per event).

## The run

1. Read `urls.txt`. If missing or empty → report "watchlist empty — add URLs to
   state/competitor-watch/urls.txt" and stop.
2. For each URL:
   a. `http_request` GET it. On failure (non-200, timeout): report the failure —
      do NOT touch the snapshot, so a flaky page isn't mistaken for a change.
   b. Normalize: strip HTML tags, scripts, styles, collapse whitespace. Keep the
      visible text — that's what "changed" means to a human.
   c. Compare against `snapshots/<name>.txt`:
      - No snapshot → save it, note "NEW — baseline recorded".
      - Same → nothing to say.
      - Different → save the new snapshot, and describe the delta: what text
        disappeared, what appeared (quote the meaningful fragments, not the
        whole page). Append a one-line event to `alerts.txt`.
3. Report: a compact list of `name: what changed` lines, or "no changes across
   N pages" when nothing moved.

## Rules

- Never fetch with credentials. If a page needs login, say so and skip it.
- Snapshots are the truth — never rewrite one except after a successful fetch.
- Keep the report under ~20 lines; this runs on a cron and lands in a chat.

## Quiet-day trigger

`scripts/check.sh` is the cron pre-filter: fetches each watched URL, compares
against its own baselines (`state/competitor-watch/trigger-baselines.txt`).
GitHub release pages compare by release-tag set only (immune to nav-counter
chrome). Silent exit = edition doesn't run = zero tokens. CHANGED/FETCH-FAIL
lines are the edition's cue.
