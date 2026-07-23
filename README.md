# OpenCrabs Surface Backup

Allowlist-only backup workspace for the unique, reusable surface of this OpenCrabs instance.

It snapshots skills, commands, dynamic tool definitions, selected non-private brain files, and selected sideloaded project code. It deliberately excludes secrets, logs, sessions, channel attachments, SQLite databases, virtualenvs, build outputs, and private memory directories.

## Commands

```text
bb backup:collect
bb backup:scan
bb backup:status
bb backup:init
bb backup:commit "backup opencrabs surface"
bb backup:restore          # dry run
bb backup:restore --apply  # explicit restore
```

## GitHub push policy

This workspace prepares a local sanitized git repo. Pushing to GitHub is intentionally not automated without explicit approval.
