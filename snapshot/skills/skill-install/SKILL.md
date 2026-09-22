---
name: skill-install
description: Install a skillpack (local directory or git URL) into OpenCrabs through the sanitized pipeline — inspect-before-install report, secret scan, commit pinning, installed_packs ledger. Triggers on "/skill-install <path-or-url>", "install this skill pack", "add this pack from github".
review_gate: true
metadata:
  version: 0.1.0
  category: automation
---

# /skill-install — skillpack installer (Grok Bot marketplace steal)

Stolen from Grok Bot's marketplace mechanics: a template installs as a
**sanitized copy** you can inspect **before** anything lands, pinned to an
exact commit, and tracked in a ledger. Nothing here mutates live config.

## Pipeline (never skip the gate)

1. **Inspect** — run the installer in dry-run mode:
   `bb ~/.opencrabs/scripts/install_skillpack.bb <pack-dir-or-git-url> --inspect`
   It validates the manifest + payload, secret-scans every file, and renders
   a report (files, LOC, sha256, per-skill tool blast radius).
2. **Show the owner** — post the report summary in chat and send the report
   file (it lands at `<home>/state/pack-inspections/<pack>.md`).
3. **WAIT for explicit approval.** No approval, no install. This skill runs
   `review_gate: true` precisely so side effects wait.
4. **Apply** — re-run with `--apply`. Payload files land under
   `~/.opencrabs/{skills,scripts,docs}/`. Existing differing files or ledger
   drift refuse (exit 4) rather than overwrite; `--force` is the explicit
   re-approval after a fresh inspect.
5. **Confirm** — list the landed files and the ledger entry to the owner.

`--home <dir>` (default `~/.opencrabs`) exists for testing; use a mktemp dir
to prove a pack without touching the live home.

## Skillpack format v1

A pack = a directory (or git repo) with:

- `skillpack.edn` — manifest: `:name :version :description :category :author
  :license :skills [{:path "skills/<name>"}] :scripts :docs`
- Payload ONLY under `skills/`, `scripts/`, `docs/` (plus root
  `skillpack.edn` / `README.md`). Extensions: `.md .edn .bb .sh .json .clj .cljc`.
- Every `skills/*/SKILL.md` must carry `description:` frontmatter.

Sanitized copy is structural: the installer reads nothing outside the pack
dir — no owner data, keys, memory, or channel state can ride along. Secret
patterns are scanned anyway (exit 3 on hit).

## SKILL.md frontmatter conventions

Skills shipped in packs should declare:

```yaml
metadata:
  version: 1.0.0        # semver of the skill itself
  category: engineering # one of the INDEX.md taxonomy categories
  update: <git-url|local path>  # where a newer version lives
```

## Ledger & updates

- Installs are recorded in `~/.opencrabs/state/installed_packs.json`
  (`name`, `version`, `source`, pinned `sha`, `installed_at`, `files`).
- Git sources are pinned to the resolved commit sha (`--pin <sha>` to force
  an exact one). Upstream drift is detected by
  `bb ~/.opencrabs/scripts/check_skill_updates.bb` — it REPORTS drift and
  never applies anything. Updates re-enter through step 1 of this pipeline.

## Exit codes

`0` ok · `1` usage · `2` validation failure · `3` secrets found · `4` collision/drift

## Out of scope (v2 ideas)

Merging pack commands into `commands.toml` / tools into `tools.toml` (live
config mutation — config_manager's territory), and publishing to a shared
directory.
