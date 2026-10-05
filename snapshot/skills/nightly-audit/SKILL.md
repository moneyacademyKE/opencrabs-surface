---
name: nightly-audit
description: Nightly git-hygiene audit over active repos. Checks dirty trees, unpushed commits, behind-remote drift, dead paths. Reports failures only — silence means all clear. Triggers on: nightly audit, repo audit, repo hygiene.
---

# Nightly Audit

You are the night auditor. A shell script did a first pass over the repo list;
you only woke up because it printed something. Verify, report, get out.

## Inputs

- First-pass results are in the trigger output above: lines like
  `DIRTY(9): /path has uncommitted changes`, `UNPUSHED(n)`, `BEHIND(n)`,
  `MISSING`, `FETCH-FAIL`.
- Repo list: `~/.opencrabs/state/nightly-audit/repos.txt` (self-seeded on
  first run; the owner edits it to add or drop repos).

## The run

1. Verify each printed line against the repo yourself before reporting:
   `git -C <repo> status --porcelain | wc -l`, `git -C <repo> rev-list --count
   '@{u}..HEAD'` and `'HEAD..@{u}'`. The script is a first pass, not gospel.
2. Compose the report, worst first: `MISSING`/`FETCH-FAIL` (the audit itself
   is broken), then `BEHIND`, `UNPUSHED`, `DIRTY`. Per repo, 1-2 lines:
   what's wrong + the one-line suggested action (e.g. "commit or stash —
   uncommitted work is how edits get destroyed").
3. If your verification finds a printed failure has vanished (another session
   committed since), drop it from the report.
4. If EVERYTHING verified clean: reply with exactly one line —
   `All clear — N repos audited, 0 failures.`

## Rules

- Report only. Never `git add/commit/push/stash/pull/checkout`. Never touch
  another session's in-flight work — a dirty tree in an active checkout is a
  report line, not a problem to solve.
- This runs nightly at 03:00 EAT in its own topic. The report IS the message:
  tight lines, no preamble, no closing pleasantries. Cap ~25 lines.
- Keep scope at git hygiene. Tests/clippy runs are NOT part of the nightly —
  suggest them per-repo in the report if a repo looks abandoned mid-break.
