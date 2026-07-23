# KB Implementation

## Current capabilities

| Capability | Status |
|---|---|
| Rust SQLite/FTS5 schema | Implemented |
| Rust upsert JSONL | Implemented |
| Rust lexical search | Implemented |
| Output modes (`titles`, `snippets`, `full`) | Implemented |
| Full-content private gate | Implemented |
| Source visibility/origin fields | Implemented |
| Ingest run ledger | Implemented |
| Babashka GitHub connector | Implemented |
| Babashka YouTube connector | Implemented |
| Babashka ask degraded path | Implemented |

## Active connectors

| Source | Status |
|---|---|
| GitHub | Native Babashka → Rust upsert; repos, READMEs, issues/PRs, optional commits |
| YouTube | Native Babashka HTTP → Rust upsert |

## Removed connectors

Gmail and Slack are not active. This keeps the KB focused on current utility and removes OAuth/private-message complexity until it is explicitly needed.
