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

This workspace prepares a local sanitized Git repository. `backup:push` and the final step of `backup:sync` still perform a real remote push, so run them only with explicit approval.

`backup:sync` composes the local `backup/push.bb` task in-process rather than spawning a bare `bb` child. That remains valid in stripped cron PATHs while preserving every scan and remote/branch safety gate.
