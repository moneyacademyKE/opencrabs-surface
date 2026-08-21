# TOOLS.md - Tool Definitions

> **Owns:** tool access, skills, routing pointers, build/runtime commands. Tool params & search/GitHub/browser routing live in the system prompt — don't duplicate them here.

## Tool Access — core set + on-demand discovery

When `[agent] lazy_tools = true`, only the CORE tools ship in every request; everything else is
pulled on demand with `tool_search` (keeps a tool-light turn from carrying ~20k tokens of unused
schemas). When the flag is off, all tools are always available and `tool_search` is just a no-op
convenience.

**Core (always available):** `read_file`, `write_file`, `edit_file`, `hashline_edit`, `bash`,
`ls`, `glob`, `grep`, `web_search`, `exa_search`, `memory_search`, `task`, `context`, `plan`,
`http_client`, `load_brain_file`, `write_opencrabs_file`, `config_tool`, `slash_command`,
`rename_session`, `follow_up_question`, `tool_search`.

**Extended (call `tool_search("…")` to discover + activate):**

| Category | What it covers | Example query |
|----------|----------------|---------------|
| `browser` | navigate / click / type / screenshot / eval on live pages | "click a button on a web page" |
| `channels` | Telegram / Discord / Slack / WhatsApp / Trello — send + connect | "send a telegram photo" |
| `agents` | spawn / wait / send-input / close / resume sub-agents, teams | "spawn a sub-agent" |
| `media` | generate / analyze images, analyze video, provider vision | "generate an image" |
| `system` | feedback_record/analyze, self_improve, rebuild, evolve, tool_manage, rsi_proposals | "rebuild from source" |
| `utility` | cron_manage, session_search, channel_search, mission_control_report, a2a_send | "create a cron job" |

Rule: if a task needs a non-core tool, call `tool_search` with a plain-words description FIRST —
never assume the capability is missing before searching.

## What belongs here

- Skill pointers (what/where to load on demand)
- Commands vs Tools vs Skills distinction
- Profile-aware paths
- Custom routing rules specific to your setup


## Skills (load on demand)

| Skill | Command | What it covers |
|-------|---------|----------------|
| Browser CDP | `/browser-cdp` | CDP automation, selectors, screenshots |
| Channels | `/channels` | Telegram, Discord, Slack, Trello, WhatsApp setup |
| Dynamic Tools | `/dynamic-tools` | tools.toml format, runtime tool management |
| SocialCrabs | `/socialcrabs` | Twitter/X, Instagram, LinkedIn automation |
| Google CLI | `/gog` | Gmail, Calendar via gog CLI |
| GitHub Workflow | `/github_workflow` | CI/CD, branch protection, release workflow |
| A2A Gateway | `/a2a-gateway` | Agent-to-Agent protocol reference |
| Servers | `/servers` | SSH aliases, Docker containers, Nginx sites |

## Commands vs Tools vs Skills

| Concept | What it is | Example |
|---------|-----------|---------|
| Tool | A function the agent calls directly | `bash`, `read_file`, `grep` |
| Command | A slash shortcut defined in commands.toml | `/check`, `/rebuild`, `/status` |
| Skill | A workflow template loaded on demand | `/browser-cdp`, `/channels` |

## Build & Runtime Commands

- `/cd <path>` — change the working directory for all tool execution (or `config_tool` `set_working_directory`); persists to config.toml
- `/rebuild` — Build, test, and hot-restart from source
- `/check` — Run `cargo clippy` and `cargo test`
- `/evolve` — Download latest release binary (full procedure → BOOT.md)

## Scheduling (Cron)

Manage scheduled jobs with the **`cron_manage`** tool (`action`: create / list / delete / enable / disable / test). Jobs run in **isolated sessions on your configured provider/model by default** — omit `provider`/`model` for the default; set `thinking: off` for routine jobs; use `deliver_to` only to send results to a channel.

**Cron expression format (the common trap):** 5 fields `min hour dom mon dow`. Day-of-week is **1-7 = Sun-Sat** (1=Sunday, 7=Saturday; `0` is invalid) — **use day names** (`Mon-Fri`, `Sun`) instead of numbers. No `@daily`/`@hourly` macros. Set `tz` (IANA, e.g. `America/New_York`) and the job runs in that zone's local time, DST-aware. **Validate before you confirm:** `create` echoes the next run times — read them back; a wrong day-of-week parses fine but the next-run list exposes it. Fix and recreate before telling the user it's set.

## Voice & Audio

STT providers: `voicebox` (local server) > `openai_compatible` > `groq` (Whisper API) > `local` (rwhisper, `local-stt` feature). Override with `stt_fallback_chain`.
TTS providers: `voicebox` (local server) > `openai_compatible` > `openai` (OpenAI TTS) > `local` (Piper, `local-tts` feature). Override with `tts_fallback_chain`.
Config: `[providers.stt.*]` / `[providers.tts.*]` in config.toml. Piper voices: `ryan`(default), `amy`, `lessac`, `kristin`, `joe`, `cori`. Local STT presets: `local-tiny`(42MB), `local-base`(142MB), `local-small`(466MB), `local-medium`(1.5GB).
Audio: all output OGG/Opus via ffmpeg. Models: whisper in `~/.local/share/opencrabs/models/whisper/`, piper in `~/.local/share/opencrabs/models/piper/`. Setup: `/onboard:voice`.

## Reporting

- `/mission-control`: analytics (tool usage, failure rates, RSI improvements, brain files), activity feed, inbox proposals, and scheduled cron jobs.
  Works in the TUI (opens the Mission Control Analytics panel) and in every
  channel (returns the report as a message). The same data is also available as
  the `mission_control_report` agent tool, so you can ask in plain language (for
  example "send me my analytics") and the agent ships the report to the chat.

## Profile-Aware Paths

| What | Path |
|------|------|
| Brain files | `~/.opencrabs/{SOUL,USER,AGENTS,TOOLS,MEMORY,CODE,SECURITY}.md` |
| Config | `~/.opencrabs/config.toml` |
| Keys | `~/.opencrabs/keys.toml` |
| Commands | `~/.opencrabs/commands.toml` |
| Plans | `~/.opencrabs/agents/session/.opencrabs_plan_<id>.json` |
| Logs | `~/.opencrabs/logs/opencrabs.YYYY-MM-DD` |

## RSI / tool-discovery recovery notes

- When an RSI/autonomous-improvement prompt mentions tools that are not visible in the current schema (`feedback_analyze`, `self_improve`, `rsi_propose`, etc.), call `tool_search("feedback analyze self improvement rsi propose")` before saying they are unavailable. Lazy tools may hide them until activated.
- `session_search` cannot fetch a session tail with an empty query. Use targeted substrings from the session title/task, or list sessions and search likely terms; do not retry empty/space queries.
- For cron auto-resume, skip the current `Cron` session when choosing “last work” unless the actual pending work is inside that cron. Prefer the most recent non-cron human/work session.
- `bash` heredocs may be blocked by the shell wrapper’s bare-REPL guard. Prefer `bb -e '...'`, `node -e '...'`, or write a temp/persistent script file before executing.

## Tool Parameter Gotchas (from feedback ledger)

Repeated avoidable failures logged against specific tools — apply proactively:

- **`session_search` supports ONLY operations 'list' and 'search'.** There is NO 'recent' operation on `session_search` — that belongs to `channel_search` (operations: 'list_chats', 'recent', 'search'). Calling `session_search` with operation='recent' fails every time with "Unknown operation 'recent'. Use 'list' or 'search'." This is the single most common avoidable failure in the ledger (100+ repeats, almost all from cron auto-resume loops). Correct usage: operation='list' to enumerate sessions, operation='search' with a **non-empty `query`** substring to find messages. Never pass an empty/missing query to 'search' ("Query cannot be empty"). To read a specific session's tail, list first, then search with a distinctive term from that session's title.
- **`read_file` fails on non-UTF8 / binary files** with "I/O error: stream did not contain valid UTF-8" (seen on `.edn` log/event files, `.marker`, `.axiom-*` artifacts). Before reading an unknown artifact, check its type with `bash` (`file <path>`, or `head -c 200 <path> | cat -v`). Parse binary logs with `bash` tools (`grep`/`strings`/`od`), not `read_file`. Recovery pattern: `bash: file <path>` → if not ASCII/UTF-8 → `strings <path> | head -50`.
- **`opencrabs_sqlite_query` is read-only (SELECT/WITH only).** Attempting INSERT, UPDATE, DELETE, or PRAGMA writes always fails with `Only SELECT/WITH queries are allowed`. For any write operation, use `bash` with `sqlite3 ~/.opencrabs/opencrabs.db "INSERT ..."` directly.

## RSI Lazy-Tool Recovery

When an autonomous RSI/self-improvement prompt says tools like `feedback_analyze`, `feedback_record`, `self_improve`, or `rsi_propose` are unavailable, do **not** conclude they are absent until you call `tool_search("feedback analyze self improvement rsi propose")`. In lazy-tool sessions these tools are commonly hidden at first; activating them turns a fake refusal loop into real work.

## Cron Auto-Resume & Recovery Discipline

When a cron auto-resume prompt asks for the last ~10 messages:
1. Call `session_search(operation="list")` to enumerate sessions.
2. Select the target session using exact session selector (`session="<exact_title>"`), avoiding broad `session="all"` searches.
3. Search with a distinctive non-empty query string from the task text (e.g. `"Phase"`, `"Task"`, `"pending"`).
4. If exact tail context is required and `session_search` cannot surface it, execute a read-only SQLite query (`SELECT * FROM messages WHERE session_id = '...' ORDER BY created_at DESC LIMIT 10;`) using `opencrabs_sqlite_query` or `bash`.
5. **Bash working directory rule**: Omit `working_directory` or set to `/` — never pass `~` or `""`. Use `cd ~/project` inside the command string.
6. **Termination rule**: If after 3 `session_search` attempts the target session cannot be located, stop searching — log the failed recovery to `feedback_ledger` (`event_type='tool_failure'`, `dimension='session_search'`) and exit cleanly. Never loop indefinitely.

## Built-in Tool Failure Triage

When a built-in/core tool shows repeated failures in the feedback ledger, do **not** propose disabling or banning that tool. Built-ins are part of the operating surface; repeated failures usually mean the agent is using the wrong parameter shape, missing a routing rule, or needs a safer wrapper/skill. The RSI move is: identify the failing invocation pattern, add precise routing/parameter guidance, or propose a narrow helper tool for the recurring safe shape. Ban only external/custom tools that are genuinely obsolete or dangerous.

## Bash Header Printing Gotcha

When writing shell commands with section headers, avoid `printf '--- header ---\n'`. Some `/bin/sh` implementations treat a leading `--` in the format string as an option and fail with `printf: --: invalid option`. Use one of these instead:

- `printf '%s\n' '--- header ---'`
- `echo '--- header ---'`

This is especially important in cron auto-resume commands where a cosmetic header failure can abort the entire progress check.

- **Always quote SSH commands**: Use double quotes for inner commands. Violations: 4, last: 2026-06-11

- **`opencrabs_sqlite_query` — known-good schema, stop guessing columns.** This is the #1 avoidable failure for the DB tool (~30+ entries on 2026-07-20..21, all `no such column: message_count` / `is_cron` / `cron` / `session_id` / `finished_at` / `params`, plus read-only-guard rejections). Read-only contract: **only `SELECT`/`WITH`** — no `PRAGMA`, no `.tables`/`.schema` dot-commands, no DML. The actual columns (verified) are:
  - **`sessions`**: `id, title, model, created_at, updated_at, archived_at, token_count, total_cost, provider_name, working_directory, category, auto_title_attempted, project_id`
  - **`messages`**: `id, session_id, role, content, sequence, created_at, token_count, cost, input_tokens, thinking, cache_creation_tokens, cache_read_tokens`
  - Other tables: `attachments, files, channel_messages, cron_jobs, usage_ledger, pending_requests, cron_job_runs, feedback_ledger, tool_executions, recent_paths, projects, goal_state, a2a_tasks` (introspect any of these with `SELECT name FROM pragma_table_info('<table>')` — that IS a read-only SELECT and works).
  - **Nonexistent columns you must NOT use:** `message_count` (compute it: `(SELECT COUNT(*) FROM messages m WHERE m.session_id = s.id)`), `is_cron` / `cron` (filter by `title = 'Cron'` or `category`), `started_at`/`finished_at` (use `created_at`/`updated_at`), `params` (not on any table). If a column is rejected, **do not retry a near-identical query** — run `SELECT name FROM pragma_table_info('<table>')` first to learn the real columns, then rewrite.

## Provider tool-call emission failure (diagnostic fingerprint)

A provider/model that **cannot emit structured `tool_use` blocks** — it narrates "I'll call the tools now" but produces zero real tool calls across many turns — is broken for agentic work. That is a provider problem, **not** a bug in your brain files. Stop adding tool rules; fix the provider. **Fingerprint** (one root cause, three signals):

- The turn repeats the same intent ("making the call now…") with **zero tool calls actually executed**, turn after turn.
- The **feedback ledger fills with 1-count hallucinated "tools"** (`DONE-DONE-DONE`, `STOP-LOOP-FINAL`, `final-final-final`, `halt-logging`, …) — text the model emitted that the ledger mis-recorded as tool names.
- **`session_context` success collapses** (seen ~13%, thousands of failures) because each text-loop iteration appends a half-serialized write, corrupting the context store ("trailing characters at line N").

**Fix:** reconfigure via `config_manager` to a function-calling-capable provider/model, or ensure a `[fallback]` chain exists so a non-tool-capable provider doesn't become sticky. An RSI/self-improvement cycle assigned to such a provider will loop until budget — it is **not resumable on that provider**. Resume it on a working provider instead (verified 2026-07-29: the stalled RSI cycle ran fine once moved off `custom:lm-studio`).


## OpenCrabs Slash Commands Reference

| Slash Command | Parameter / Option | Description & Action |
| :--- | :--- | :--- |
| `/plan <goal>` | Goal string | Switches session to `/plan` drafting model (`cx/gpt-5.6-sol`) and initializes plan. |
| `/execute` | None | Swaps to execution model and runs approved plan steps to completion. |
| `/models <name>` | `<provider>/<model>` | Switches active provider & model (e.g. `/models infer/cx/gpt-5.6-terra`). |
| `/compact` | None | Forces immediate context compaction & summary generation. |
| `/doctor` | None | Runs system health audit across config, keys, database, and channels. |


## Vision & Media Coordinate Scaling

1. **Screenshot Scale Factor**: When computing click coordinates or crop regions from a screenshot, always check whether the image was downscaled. If the displayed resolution differs from the on-disk resolution, multiply all coordinates by `(original_width / displayed_width)` before using them.
2. **Notebook (.ipynb) Handling**: When reading `.ipynb` files, skip base64-encoded cell outputs and focus on source cells. For large dataframe outputs (>10,000 chars), extract the shape/columns with `jq` instead of reading the full output.


## Open-Model Tool Call Recovery

1. **Common Schema Mistakes to Self-Check**: Before retrying a failed tool call, check if you made one of these 4 common mistakes: (1) sent `null` for an optional field instead of omitting it, (2) sent a JSON array as a string (`"[\"a\"]"` instead of `["a"]`), (3) wrapped a single argument in `{}` where the schema expected an array, (4) passed a bare string where an array was expected. Fix the shape, don't just retry.
2. **Transparent Defaults**: When a tool requires coupled fields and you supply a default for a missing one (e.g. `limit=2000` when only `offset` was given), state the default in your response so the user can correct it.
3. **Config Manager Discovery Preflight**: Before any `config_manager` `action=get` or `action=set` call — including within complex multi-tool sessions — first call `action=list_keys` (or read `~/.opencrabs/config.toml` directly via `bash`) to confirm the section path and key name exist. Never assume a key name from memory; the TOML schema is ground truth.
4. **File Creation Directory Preflight**: Set `create_dirs: true` when invoking `write_file` on paths where parent directories may not exist.


## Evidence-First Tool Economy

**Minimize redundant calls, never independent evidence.** Classify every call as discovery, decision, mutation, or proof; skip it only when a fresh receipt already answers it. Batch independent read-only work, but keep dependencies sequential. Recheck immediately before each mutation and verify it once with targeted proof. Invalidate receipts after writes or relevant git/config/external changes; always retain full gates for releases, security, and irreversible actions.

**Progress-Tranche Guard:** Limit uninterrupted orchestration to 10 tool calls. Before call 11, publish a concise checkpoint naming the verified state transition and next bounded action. On resume, continue from the last failed assertion instead of rebuilding orchestration. Use one control plane—plan or autonomous goal, never both—and permit only one writer per file/worktree.

- **Shape-first jq: probe before you pipe.** Before running a jq query against a new or unfamiliar JSON file, probe the shape once with `jq 'type, keys' <file>` (or `jq 'type'`), then write the query against the verified shape and guard iterations with `?` / `// empty` (e.g. `.items[]?.id`). Assume-shape queries caused 16 bash failures Aug 14–16 (`Cannot iterate over null`, `null has no keys`). Also: the string test is `startswith`, never `startsWith`. Violations: 16, last: 2026-08-16.

- **Beads-loop bankai: queue from the graph, not the list.** When bankai-bound work exists, select with `bankai ready` (dependency-aware) instead of `bankai list`. Always pass `depends_on` (comma-separated OpenCrabs plan task ids) when calling `bankai_plan_task_start` — deps wire as blocking edges automatically. File mid-task discoveries as linked bankai tasks (create + `dep add`), never fix-and-forget in-session. Violations: deps on 1/21 tasks, discoveries 0/4 filed, last: 2026-08-15.

- **tools.toml optional params leak the literal `{{placeholder}}` when omitted.** The dynamic-tool engine substitutes only params the caller supplies; an omitted optional param reaches the shell command as the raw `{{name}}` token (verified live: `bankai_plan_task_start` without `depends_on`, 2026-08-17). Guard consumer scripts — skip any arg matching `'{{'*'}}'` — or set a TOML `default = ""` on the param. Violations: 1, last: 2026-08-17.

**telegram_send large documents: one retry, then link.** Uploads >~30MB fail deterministically on this host ("A network error: error sending request for url …/SendDocument" — 15 lifetime failures incl. arm64 + linux tarballs, 2026-08-19). After ONE retry, stop hammering the upload and deliver a direct download URL (e.g. GitHub release asset) + sha256 + curl one-liner instead.

**analyze_image provider failures: fall back to native vision, don't retry.** Dominant failure mode (10/18 lifetime failures): "All provider vision candidates failed: No text response from vision model" — provider-side vision flakiness, not path misuse (checked 2026-08-20). On this error do not re-call analyze_image; read the image file directly with the model's own vision (read_file on the path) or route to a vision-capable provider. Violations: 0, rule derived from ledger.

**a2a_send: preflight discover, don't blind-send.** 8/16 lifetime failures cluster into: (1) peer unreachable (`error sending request for url 127.0.0.1:…`) → call action='discover' first; a dead endpoint is a report, not a retry; (2) `Task not found` on get/cancel → the task_id is stale, re-send instead of polling it; (3) peer HTTP 500 (`clojure.lang.Atom` method errors) → peer-side bug, report it, never retry; (4) `Message must contain at least one text part` → never send an empty message. Violations: 0, derived from ledger 2026-08-20.

**bankai_plan_task_start preflight: gate, daemon, then shape.** 4/14 lifetime failures: (1) OpenCrabs plan gate raised → seed `plan add_tasks` + `plan start` before any bankai call; (2) `bankai daemon not healthy` → run `bankai_doctor` (and `bankai serve` if needed) first; (3) `jq: Cannot iterate over null` → probe JSON shape before piping (see shape-first jq rule); (4) shell `unexpected EOF` quoting error → write a script file instead of an inline one-liner. Violations: 0, derived from ledger 2026-08-20.
