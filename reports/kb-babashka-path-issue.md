## Problem

Knowledge-base ingestion succeeds only when `bb` is inherited on `PATH`. `scripts/kb.bb` launches active connector scripts through `run-proc "bb" script args`; OpenCrabs cron/shell contexts may run with `PATH=/usr/bin:/bin`, where Homebrew's Babashka binary is absent.

## Reproduction

1. Run the KB task through `/opt/homebrew/bin/bb` with `PATH=/usr/bin:/bin`.
2. Invoke `kb:ingest github` or `kb:ingest youtube`.
3. The outer task starts, but the nested connector launch cannot resolve bare `bb`.

## Root cause

The task dispatchers in `bb.edn` are already pinned to `/opt/homebrew/bin/bb`, but `scripts/kb.bb` has a second-level bare executable invocation in `ingest-native`.

## Fix plan

- Pin the nested connector launcher to the verified Babashka executable used by the task dispatchers.
- Preserve all connector arguments and the existing run-ledger failure behavior.
- Add focused stripped-PATH verification for `kb:ask` and an ingestion failure-path probe that proves the connector process is launched instead of failing executable lookup.

## Scope

- `projects/knowledge-base/files/scripts/kb.bb`
- Backup snapshot/policy documentation as needed to retain the durable, allowlisted surface.
