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
bb kb:doctor
bb kb:stats
bb kb:search --mode snippets "query"
bb kb:ingest github
bb kb:ingest youtube --handle @channel --max 30
```

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
