# HEARTBEAT.md — Autoheal Pulse

> **Owns:** the periodic autoheal routine — detecting & resuming incomplete work when the model was temporarily unavailable (rate limit, outage, transient API failure mid-turn).

## Why this exists
The model API can fail mid-turn. When it does, multi-step work gets cut off: a plan/task sits in `in_progress`, an inflight ledger entry never gets cleared. This pulse catches that and heals it — without spamming you when everything's fine.

## Inflight discipline (hard rule for the main agent)
- **Starting non-trivial multi-step work** (and NOT using the `plan` tool) → append one line to `~/.opencrabs/state/inflight.md`:
  `- [ISO timestamp] <session-id> — <what you're doing>`
- **Finishing that work** → remove the line.
- A stale entry (>15 min, no clear) = the model was likely unavailable mid-task.
- If you ARE using the `plan` tool, the plan file itself is the signal — no inflight entry needed.

## Incompleteness signals (checked every pulse, in order)
1. **Inflight ledger** `~/.opencrabs/state/inflight.md` — live entry with timestamp >15 min old → interrupted.
2. **Plan files** `~/.opencrabs/agents/session/.opencrabs_plan_*.json` — a plan whose active task is `in_progress`/started but not `completed`, not updated recently → interrupted. Read the pending task's description to know what to resume.
3. **Task manager** `task_manager list` (show_completed=false) — any `in_progress` task → interrupted.

## Heal procedure
1. Run all three checks above.
2. **If incomplete work found:**
   - Re-derive the pending step from the plan/inflight/task description.
   - **Unambiguous** (a specific file edit, a specific command) → redo it, verify it landed, then clear the inflight entry / mark the plan task complete.
   - **Ambiguous** (needs a human call) → do NOT guess. Notify the user what was interrupted and that you're holding.
   - Report as PLAIN TEXT output (2–3 lines max: *what got cut off + what you did*). The cron/delivery pipeline sends it to Telegram automatically.
   - Do NOT call telegram_send or "check telegram status" — heartbeat sessions are isolated and have no telegram channel binding; that would falsely report "no connection." Delivery is automatic via deliver_to.
3. **If nothing incomplete** → stay silent. End the turn. Do not message.

## Rules
- Never redo already-complete work.
- Never spam — this pulse speaks ONLY when it heals something (or hits an ambiguous block).
- Keep notifications tight. This is a healing pulse, not a chat.
- Don't create new inflight entries yourself; you only read & clear.

## Cadence
Every 15 minutes, drift-OK. Scheduled via `cron_manage`. Delivers to Telegram only on heal/hold.
