---
name: security-audit
description: Scored 0-100 security and CVE audit of a repo - dependency CVEs, secrets, auth surfaces, input handling, config. Findings ranked with file receipts. Invoke as /security-audit <repo-path>. Report-only.
---
# security-audit

No state, no schedule. Invoke: `/security-audit <repo-path>`.

## Run

1. **Inventory** (read-only): languages, dependency manifests, entry points, auth surfaces, config files, network egress. State what you found before judging it.
2. **Native tooling** where the repo has it: `cargo audit`, `npm audit`, `gitleaks`, `trivy` — only what is already installed. Never install anything mid-audit. Missing tooling is a note, not a failure.
3. **Manual pass**, in this order: secrets and credentials (including history-aware checks on tracked files), dependency CVEs, authentication/authorization logic, input parsing and injection surfaces, config and permissions, network egress and data exposure.
4. **Score 0-100** with a per-area breakdown: deps, secrets, auth, input, config, supply chain. Deduct by severity, not by count.
5. **Findings** ranked by severity. Each one carries: file:line receipt, why it matters, minimal fix sketch (2 lines max).

## Rules

- Never claim a finding without reading the code that proves it. "Looks risky" is not a finding.
- Report-only: no fixes applied, no files modified, no pushes. Fix sketches are prose.
- State what was NOT checked (no creds access, binary blobs skipped, tooling absent). An audit that hides its blind spots is marketing.
- Deliver the report as a .md file in the topic, not a chat wall.

Score bands: 90+ ship-ready, 70-89 minor work, 40-69 real risk, below 40 do not deploy.
