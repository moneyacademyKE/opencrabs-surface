---
name: changelog-scribe
description: Weekly release-notes drafts from commits since the last tag per repo, written as draft files - never published without owner approval.
---
# changelog-scribe

Turns a week of commits into release-note drafts. The owner publishes; the crab never does.

## State

- `~/.opencrabs/state/changelog-scribe/repos.txt` - absolute repo paths, one per line, `#` skipped.
- `~/.opencrabs/state/changelog-scribe/last_run.txt` - ISO date of the last draft run.

## Run

1. Per repo, pick the base: `git -C <path> describe --tags --abbrev=0` if tags exist, else the date in `last_run.txt`, else 7 days ago.
2. `git -C <path> log <base>..HEAD --oneline` and group by prefix: **Added** (feat), **Fixed** (fix), **Changed** (everything else).
3. Write each repo's draft to `~/.opencrabs/projects/changelog-scribe/drafts/<date>-<reponame>.md` (create dirs as needed). Draft format: version-agnostic, bulleted, human-readable lines - no raw hashes unless the change is un-namable.
4. In the topic: max 3 lines per repo - the draft path plus one headline of the week. Nothing else.
5. Update `last_run.txt` only after ALL drafts are written.

## Rules

- DRAFT-ONLY: never push, never tag, never edit a GitHub release. Owner approval is the publish path.
- Read-only git.
