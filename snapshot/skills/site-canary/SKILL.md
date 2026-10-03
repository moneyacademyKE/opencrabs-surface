---
name: site-canary
description: Daily uptime canary for a URL watchlist - tattles only on failures (non-200, timeout, slow over 3s). Silent on green days.
---
# site-canary

Report-only monitor. Never fixes anything, never edits the watchlist on its own.

## State

- `~/.opencrabs/state/site-canary/urls.txt` - one `URL|label` per line. `#` comments and blank lines skipped.
- Stateless: no snapshots, no history. Each run is a fresh check.

## Run

1. Execute `bb /Users/moe/.opencrabs/skills/site-canary/check.bb`. It prints ONLY failures, one per line: `label | problem | url`.
2. Empty output: reply exactly `site-canary: all green (N URLs).` where N is the non-comment line count of urls.txt. Nothing else.
3. Failures: one line each, `<label> - <problem>`, plus at most one short likely-cause line covering the batch (e.g. "all three failing together smells like local network, not the sites").
4. Never retry a failing URL within a run. One check each, no storms.

## Rules

- Adding or removing watched URLs needs the owner's say-so. Suggest, don't edit.
- The check is curl-based and authless: a URL that needs a login or a browser is out of scope - say so if one is added.
