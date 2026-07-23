# KB Connectors

| Source | Implementation | Status | Notes |
|---|---|---|---|
| GitHub | `scripts/github_ingest.bb` → Rust `upsert-jsonl` | Active/native | Uses `gh api`; emits repos, READMEs, issues/PRs, optional commits. |
| YouTube | `scripts/youtube_ingest.bb` → Rust `upsert-jsonl` | Active/native | Direct HTTP; requires `YOUTUBE_API_KEY` only when enabled. |

## Inactive sources

Slack and Gmail are removed from the active KB surface. They are not listed in config examples, Babashka tasks, or the OpenCrabs skill. Reintroduce them only after an explicit privacy/OAuth design pass.

## Contract

Every connector emits JSONL:

```json
{"source":"github","source_id":"issue:owner/repo:1","ts":"2026-01-01T00:00:00Z","title":"Title","content":"Body","metadata":{},"visibility":"internal","origin":"team"}
```

Required: `source`, `source_id`, `title`, `content`.
Preferred: `ts`, `metadata`, `visibility`, `origin`.
