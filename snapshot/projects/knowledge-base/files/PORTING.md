# Porting Notes

The KB is now sideloaded OpenCrabs infrastructure, not a Python starter.

```text
OpenCrabs skill
  ↓
Babashka orchestration
  ↓
Rust KB sidecar
  ↓
SQLite + FTS5
```

## Runtime policy

- Rust owns schema, upsert, stats, search, policy-aware output modes, and ingest run ledger.
- Babashka owns connector orchestration and source adapters.
- GitHub and YouTube are active.
- Gmail and Slack are inactive and intentionally absent from active commands/docs.

## Privacy policy

Raw `search` / `ask` output may contain private source content. Shared/group contexts should prefer stats, doctor, titles, or authored summaries. Full content requires `--private`.

## Upgrade survival

All code/data lives under `~/.opencrabs/projects/knowledge-base/`, `~/.opencrabs/skills/`, and `~/.opencrabs/tools.toml`, so `/evolve` does not wipe it.
