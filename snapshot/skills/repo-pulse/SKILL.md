---
name: repo-pulse
description: Daily GitHub pulse for a watched-repo list - new issues, PRs, releases and failed CI since the last run, via gh. Report-only; baselines on first run.
---
# repo-pulse

Watches repos so the owner doesn't have to. Speaks only when something needs eyes.

## State

- `~/.opencrabs/state/repo-pulse/repos.txt` - one `owner/repo` per line, `#` skipped.
- `~/.opencrabs/state/repo-pulse/since.txt` - ISO date of the last successful run. Absent = first run.

## Run

1. Read the list. No `since.txt`: write today's date to it and reply exactly `repo-pulse: baseline set for N repos - first report tomorrow.` Stop there.
2. For each repo, via `gh`:
   - `gh search issues --repo <r> --updated ">=SINCE" --state open --limit 10`
   - `gh search prs --repo <r> --updated ">=SINCE" --limit 10`
   - `gh release list -R <r> --limit 5` (keep only releases newer than SINCE)
   - `gh run list -R <r> --status failure --created ">=SINCE" --limit 5`
3. Report only items that need eyes: genuinely new issues/PRs, new releases, red CI. One line each: `repo #N - what - link`. Cap 15 lines; overflow becomes one `...and K more` line.
4. Nothing found: `repo-pulse: quiet (N repos since SINCE).`
5. Update `since.txt` to today ONLY after a fully successful pass. A run with gh errors leaves the date alone so nothing is silently skipped.

## Rules

- Report-only: never comment, close, merge, or re-run CI. Ever.
- `gh` missing or unauthenticated: say exactly that in one line. No improvised unauthenticated API calls.
