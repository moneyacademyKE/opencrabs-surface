## Resolution

### Root cause

The knowledge-base task dispatcher had already been hardened with `/opt/homebrew/bin/bb`, but `scripts/kb.bb` still launched GitHub and YouTube connector scripts through a second-level bare `bb` process. Stripped cron/shell `PATH` values therefore broke nested ingestion even though the outer task launched correctly.

### Fix

- `scripts/kb.bb` now defines `babashka-bin` as `/opt/homebrew/bin/bb` and invokes connectors through that explicit executable.
- Its `run-proc` helper now passes the inherited environment explicitly, so stripped-PATH test contexts are preserved rather than silently replaced by the process library's default environment.
- `bb.edn`, the dynamic `kb_ask` command, and user documentation retain the explicit executable at their outer launch boundary.
- `backup:sync` continues to compose `backup/push.bb` in process; it no longer invokes a bare `bb` subprocess and preserves scan/remote/branch guards.

### Verification

- `/opt/homebrew/bin/bb --version` → `babashka v1.12.218`.
- With `PATH=/usr/bin:/bin`, both direct `scripts/kb.bb ask opencrabs` and task-form `kb:ask opencrabs` exited `0` and returned citation candidates.
- With the same stripped PATH, direct `scripts/kb.bb ingest youtube --handle @path-probe --max 1` exited `2` at the expected missing `YOUTUBE_API_KEY` gate—no bare-`bb` lookup failure occurred.
- Static scan found no remaining `run-proc "bb"`, `process ["bb"]`, or `shell "bb"` executable invocations in the backup/KB automation scope.
- Sanitized backup collection and scan passed: 55 files collected; no denied paths, oversized files, binaries, or secret-looking patterns.
- `/doctor` reported healthy keys, configured provider, and enabled Telegram channel.

No commit, PR, push, or deployment was performed.
