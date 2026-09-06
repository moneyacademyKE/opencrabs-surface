---
name: substack
description: "Connect a Substack publication to OpenCrabs via the official Substack MCP server, then answer publication questions live (subscriber counts, revenue, post performance, traffic sources, retention). Read-only — the server cannot publish posts, send Notes, or modify the account, and has NO access to profile data or Notes activity. Requires a Bestseller-badge publication + admin on the account. Setup mode wires the `mcp` CLI bridge (~/.config/mcp/servers.json + OAuth browser flow) and exposes tools via tools.toml; query mode routes publication questions through those tools. Triggers on \"/substack setup\", \"/substack <question>\", \"my subscriber count\", \"how is my newsletter doing\", \"substack stats\", \"publication revenue\", \"which traffic source drove the most views\"."
metadata:
  version: 0.1.0
  source: "https://support.substack.com/hc/en-us/articles/50834026608916-How-to-connect-Substack-to-your-AI-Assistant"
  mcp_server: "https://mcp.substack.com/api/v1/mcp"
---

# /substack — Substack publication data via the official MCP server

Substack exposes a first-party, read-only MCP server. OpenCrabs talks to it through the
`mcp` CLI shell bridge (OpenCrabs is not a native MCP client — this is the documented path
from the README, "Connecting to Remote MCP Servers").

Two modes. If setup is incomplete, the query answers degrade to "not connected" — run setup.

## Server facts (verified 2026-09-05)

| Fact | Value |
|---|---|
| MCP server URL | `https://mcp.substack.com/api/v1/mcp` |
| Auth | OAuth 2.0 (authorization server: `https://substack.com`), Bearer header, scope `mcp:read` |
| Access | Read-only: dashboard metrics, traffic data, publication settings |
| Cannot do | Publish posts, send Notes, modify account, read profile data or Notes activity |
| Eligibility | Admin on the publication AND publication has the **Bestseller** badge |
| OAuth token cache | `~/.config/mcp/auth.json` (handled by the `mcp` CLI, auto-refreshed) |

## Mode A — Setup (`/substack setup`)

Run these steps in order; stop and report at the first failure.

### 1. Eligibility check (ask, don't guess)

Confirm with the user before touching config:
- Are they an **Admin** on the publication?
- Does the publication have the **Bestseller** badge? (An ineligible publication connects
  fine at the protocol level but errors on data calls — catch it here instead.)

### 2. Install the `mcp` CLI (skip if `which mcp` succeeds)

```bash
curl -sSL https://raw.githubusercontent.com/avelino/mcp/main/install.sh | sh
mcp --help   # verify
```

### 3. Register the server

Add to `~/.config/mcp/servers.json` (merge if the file exists — read it first):

```json
{
  "mcpServers": {
    "substack": {
      "url": "https://mcp.substack.com/api/v1/mcp"
    }
  }
}
```

No headers needed — OAuth is handled by the CLI (opens a browser, saves the token).

### 4. Authenticate + discover tools

```bash
mcp substack --list     # triggers the OAuth browser flow on first run
mcp substack --info     # tools WITH input schemas — these names drive step 5
```

If multiple publications are on the account, the flow asks which to link —
**select the Bestseller publication**.

### 5. Wire tools into OpenCrabs

For each useful tool from `--info`, add a `[[tool]]` block to `~/.opencrabs/tools.toml`.
Template (fill in the real tool name + params from `--info` output):

```toml
[[tool]]
name = "substack_<tool_name>"
description = "Substack MCP: <what it returns>"
executor = "shell"
command = "mcp substack <tool_name> '{{json_args}}'"
timeout_secs = 30
requires_approval = false
enabled = true

[[tool.params]]
name = "json_args"
type = "string"
description = "JSON arguments per the tool's input schema, e.g. {\"publication\":\"<subdomain>\"}"
required = true
```

Shell-bridge gotchas (from the OpenCrabs README — do not improvise around these):
- Always `executor = "shell"`; the HTTP executor has known JSON-templating issues.
- JSON args go in **single quotes**, inner quotes escaped `\'`.
- Set `timeout_secs` — MCP calls can be slow.
- If the CLI exits non-zero, OpenCrabs treats it as tool failure; check stdout/stderr.

### 6. Verify

Run one real query through the wired tool (e.g. current subscriber count). A clean JSON
response = setup complete. Report which tools are live.

## Mode B — Query (`/substack <question>`)

Preconditions: setup done, `mcp substack --list` works, tools wired in tools.toml.

1. Map the question to the narrowest wired tool. Do not fan out to every tool for a
   single-number question.
2. Execute via the wired tool. If the response is an auth error, the OAuth token likely
   expired mid-flow — re-run `mcp substack --list` to refresh, then retry once.
3. Answer with the numbers, the trend, and the source window. Publication data questions
   deserve actual data, not vibes.

### What this answers well (from Substack's docs)

- "What's my current subscriber count and revenue?"
- "How is my paid subscriber retention trending after 6 months?"
- "Which traffic source drove the most views on my last post?"
- "How many free subscribers have I gained since January?"

### Hard limits (do not fake around them)

- **No Notes data.** Notes activity and profile stats are not exposed by the server —
  say so plainly if asked, don't approximate from post data.
- **Read-only.** It cannot publish, send, or modify anything. If asked to publish,
  route to Substack itself.
- **Bestseller gate.** Non-Bestseller publications get an eligibility error — that's
  Substack's policy, not a bug in the bridge.

## Error playbook

| Symptom | Cause | Fix |
|---|---|---|
| `missing_token` / 401 | No OAuth token yet, or expired | Re-run `mcp substack --list` (browser flow), retry once |
| Eligibility error | Publication lacks Bestseller badge | Not fixable via config; tell the user |
| `mcp: command not found` | CLI not installed | Setup step 2 |
| Tool returns empty | Wrong publication linked | Re-auth, select the right publication |
| Cloudflare challenge page | Hit the endpoint with plain curl | Don't — always go through the `mcp` CLI |
