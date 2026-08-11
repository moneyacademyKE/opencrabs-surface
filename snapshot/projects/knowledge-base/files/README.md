# OpenCrabs Knowledge Base

Local-first knowledge base for OpenCrabs. Active sources are **GitHub** and **YouTube**.
Gmail and Slack were removed from the active surface because they are currently unnecessary and carry heavier privacy/OAuth complexity.

## Shape

```text
OpenCrabs skill
  ↓
Babashka orchestration
  ↓
Rust KB sidecar
  ↓
SQLite + FTS5
```

## Paths

- Project: `~/.opencrabs/projects/knowledge-base/`
- Operator dir: `~/.opencrabs/projects/knowledge-base/files/`
- Rust binary: `~/.opencrabs/projects/knowledge-base/bin/kb`
- DB: `~/.opencrabs/projects/knowledge-base/files/nsm_kb.sqlite`

## Commands

```bash
cd ~/.opencrabs/projects/knowledge-base/files
/opt/homebrew/bin/bb kb:doctor
/opt/homebrew/bin/bb kb:stats
/opt/homebrew/bin/bb kb:search --mode snippets "query"
/opt/homebrew/bin/bb kb:ingest github
/opt/homebrew/bin/bb kb:ingest youtube --handle @channel --max 30
```

The explicit Babashka executable avoids relying on an ambient PATH, which may be intentionally stripped in automated contexts.

## Active sources

| Source | Runtime | Notes |
|---|---|---|
| GitHub | Babashka + `gh api` + Rust upsert | Repos, READMEs, issues/PRs, optional commits. |
| YouTube | Babashka HTTP + Rust upsert | Video metadata/descriptions. Requires `YOUTUBE_API_KEY`. |

## Policy

Search supports output modes:

- `titles` — metadata/title only
- `snippets` — short excerpts, default
- `full` — raw content, requires `--private`

Documents carry `visibility` and `origin` fields. Defaults:

- GitHub: `visibility=internal`, `origin=team`
- YouTube: `visibility=public`, `origin=public_web`

## Removed sources

Slack and Gmail are intentionally inactive. Do not re-enable them by accident; add them back only with explicit privacy gates and a fresh connector decision.
