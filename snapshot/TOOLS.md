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
- `bash` heredocs may be blocked by the shell wrapper’s bare-REPL guard. Prefer `python3 -c '...'`, `node -e '...'`, or write a temp/persistent script file before executing.

## Tool Parameter Gotchas (from feedback ledger)

Repeated avoidable failures logged against specific tools — apply proactively:

- **`session_search` supports ONLY operations 'list' and 'search'.** There is NO 'recent' operation on `session_search` — that belongs to `channel_search` (operations: 'list_chats', 'recent', 'search'). Calling `session_search` with operation='recent' fails every time with "Unknown operation 'recent'. Use 'list' or 'search'." This is the single most common avoidable failure in the ledger (100+ repeats, almost all from cron auto-resume loops). Correct usage: operation='list' to enumerate sessions, operation='search' with a **non-empty `query`** substring to find messages. Never pass an empty/missing query to 'search' ("Query cannot be empty"). To read a specific session's tail, list first, then search with a distinctive term from that session's title.
- **`read_file` fails on non-UTF8 / binary files** with "I/O error: stream did not contain valid UTF-8" (seen on `.edn` log/event files, `.marker`, `.axiom-*` artifacts). Before reading an unknown artifact, check its type with `bash` (`file <path>`, or `head -c 200 <path> | cat -v`). Parse binary logs with `bash` tools (`grep`/`strings`/`od`), not `read_file`.

## RSI Lazy-Tool Recovery

When an autonomous RSI/self-improvement prompt says tools like `feedback_analyze`, `feedback_record`, `self_improve`, or `rsi_propose` are unavailable, do **not** conclude they are absent until you call `tool_search("feedback analyze self improvement rsi propose")`. In lazy-tool sessions these tools are commonly hidden at first; activating them turns a fake refusal loop into real work.

## Cron Auto-Resume Tail Recovery

When a cron auto-resume prompt asks for the last ~10 messages, remember `session_search` cannot do tail reads: it only supports `list` and `search`, and `search` requires a non-empty substring. Do this instead:

1. `session_search(operation="list")` to identify candidates.
2. Prefer the newest non-`Cron` work session unless the cron session itself is the real target.
3. Search that session with distinctive terms from its title or expected task (`"Phase"`, `"Task"`, `"pending"`, project name).
4. If exact tail context is still required and tools cannot provide it, use a read-only `sqlite3 ~/.opencrabs/opencrabs.db` query as a last resort; do not write to the DB.

Never retry `session_search` with `operation="recent"`, an empty query, or whitespace-only query — that is pure failure spam.

## Cron/Bash Tool Recovery Notes

- **Cron auto-resume:** `session_search` cannot return the last N messages directly and `search` requires a non-empty query. After `session_search(operation="list")`, prefer the newest non-`Cron` work session, search it with distinctive terms, and if true tail context is needed use a read-only `sqlite3 ~/.opencrabs/opencrabs.db` query against `messages`/`sessions` as the last-resort inspection path.
- **Bash working directories:** do not set `working_dir` to `~` or `""`; the tool validates it literally and fails before the shell can expand it. Use `working_dir: "/"` or omit it, then `cd ~/path` inside the command when shell expansion is needed.

## Cron Auto-Resume Exact-Session Rule

When resuming a non-cron session from a cron heartbeat, avoid searching `session="all"` with a generic title token like `RSI` after listing sessions: the search can match the current Cron session because prior tool outputs echo that title. Use the exact target session selector (`session="RSI autonomous cycle"`, `session="Axiom Phase 5-8 Completion"`, etc.) plus a non-empty distinctive query from the task text. If that still does not surface the actual last messages, use the documented read-only DB fallback rather than retrying broad searches.

For bash checks in cron, omit `working_directory` entirely or set it to `/`; never pass `~` or an empty string. Put `cd ~/project` inside the command so the shell expands it. Avoid `python3 - <<'PY'` heredocs in this harness because the bare-REPL guard may misclassify them; use `python3 -c`, `node -e`, or write a small script file and run it.

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
