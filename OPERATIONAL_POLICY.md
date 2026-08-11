# Shell and Babashka Operational Policy

## Decision

Keep OpenCrabs' `bash` tool as the general-purpose shell executor. It remains responsible for arbitrary system commands, pipes, redirects, Git/GitHub CLIs, process isolation, timeouts, and related runtime safeguards.

Use Babashka for authored automation. Do not substitute Babashka for the shell executor: `bb` evaluates Clojure and cannot preserve general POSIX shell semantics.

## Verified Runtime

- Platform: macOS/Homebrew
- Babashka executable: `/opt/homebrew/bin/bb`
- Verified version: `1.12.218`
- A stripped `PATH=/usr/bin:/bin` cannot resolve a bare `bb` child process.

## Applied Fixes

| Surface | Change | Reason |
|---|---|---|
| `backup/sync.bb` | Loads `backup/push.bb` in-process with cleared task arguments | Removes the bare `bb backup:push` subprocess while retaining scan, remote, and branch guards. |
| Knowledge-base `bb.edn` and `scripts/kb.bb` | Pins task dispatchers and nested connector launches to `/opt/homebrew/bin/bb` | Makes both launch levels independent of stripped cron PATHs. |
| `tools.toml` `kb_ask` | Pins the dynamic-tool command to `/opt/homebrew/bin/bb` | Makes the outer tool launch explicit; the inner BB task is also pinned. |

## Verification Evidence

- `backup:sync` and `scripts/kb.bb` load successfully under a stripped PATH.
- `backup:sync` under a stripped PATH reaches its expected missing-remote safety gate, rather than failing with `Cannot run program "bb"`.
- A nested YouTube connector probe under the same PATH exits `2` for its expected missing `YOUTUBE_API_KEY` prerequisite, without any bare-`bb` lookup failure.
- `kb:ask` under a stripped PATH exits `0` and returns citation candidates.
- `opencrabs doctor` reports healthy keys, provider, and enabled Telegram channel.

## Dynamic Tools

No new dynamic tools were created. Top-level tool volume alone is insufficient evidence of a repeated, stable, parameterized workflow; adding wrappers speculatively would create incidental complexity.

Promote a workflow to a typed tool only after evidence shows the same safe command shape recurs and the input boundary can be constrained better than the general shell.

## Rich Hickey Certification

Approved: the change removes a fragile dependency on ambient process state, composes existing code instead of adding a new abstraction, and preserves the general shell capability where it belongs.
