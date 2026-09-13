# EXPERIMENTS.md — autoresearch log

Every measurement run appends here. Format: date, eval set, claims tested, verdicts, action taken.

## Run 001 — 2026-09-12 — initial claim verification

**Eval set:** `DAFj1rOFszE` (13 pages, 1080×1080 PNG export, `/tmp/rotary-deck/`)
**Harness:** `scripts/taste_probe.swift` (OCR geometry + band palettes + luminance) → `results.jsonl` → `scripts/score_claims.clj`

| Claim | Verdict | Support | Measured detail |
|---|---|---|---|
| H1 masthead in top 33% | CONFIRMED | 13/13 | all masthead strings y<0.33 on all pages |
| H2 strict footer order WHEN→TIME→WHERE→CONTACT | REFUTED | 5/11 | real law: weekday opens (y≈0.72–0.80), contact closes (y≈0.95–0.96); time/venue interleave |
| H3 dot-time "6.30PM" | CONFIRMED | 9/9 | zero colon-style times |
| H4 all big text caps | REFUTED | 6/13 | true scope: headline lines caps on event posters; digits/bio pages pollute |
| H5 blue+gold palette omnipresent | REFUTED | blue 4/13, gold 3/13 | navy scrim + off-white dominate; blue/gold are accents, mostly light pages |
| H6 two page modes (dark/light) | CONFIRMED w/ refinement | 10/13 | 8 dark, 2 light, 3 hybrid — modes real, boundary pages exist |
| H7 hierarchy contrast | CONFIRMED | 13/13 | headline/median line-height ratio 16–38× (replaces unmeasured 8:4:3:2) |
| H8 headline ≤2 big lines | REFINED | 7/13 strict | headline class = 2 lines on 9/13 event posters; date digits are a separate big-text class |
| H9 square 1080×1080 | CONFIRMED | 13/13 | — |
| H10 min text ≥0.017 canvas | RULE, deck violates | 9/13 comply | 4 pages ship 0.013–0.015 (unreadable in feed); kept as enforced rule |

**Ratchet action:** SKILL.md v2 — REFUTED claims rewritten to measured truth (footer order, palette, caps scope), unmeasured estimate (8:4:3:2) replaced by measured range (16–38×), all rules tagged with evidence class.

## Template for future runs

```
## Run NNN — date — hypothesis
**Eval set:** ...
**Change tested:** ...
**Metric:** ...
**Verdict:** KEEP / REVERT
**Action:**
```

## Run 002 — 2026-09-13 — reproducibility + new-hypothesis sweep

**Eval set:** `DAFj1rOFszE` re-exported (13 pages, /tmp/rotary-deck/)
**Harness:** unchanged for H1–H10; Run-002 extension appended (labeled) with H11–H16.

**Reproducibility: 10/10 verdicts identical to Run 001.** The harness is
deterministic; every [verified] tag in v2 survives re-measurement.

| New claim | Verdict | Support | Measured detail |
|---|---|---|---|
| H11 weekday opens y∈[0.70,0.85] | REFINED | 8/11 | wrong window, right pattern: all 11 weekday lines sit y∈[0.60,0.80] — band widened to measured range |
| H12 contact closes y≥0.88 | CONFIRMED | 5/5 | enquiries lines at y 95–96, rock solid |
| H13 big text mid-band only | REFINED | 22/32 | big text lives in THREE zones: wordmark top (y 6–16), headline mid, date digits footer — claim rescoped |
| H14 ≤1 novelty accent | CONFIRMED | 12/13 | only page 10 carries two novelty hues |
| H15 big text centered | REFINED | 24/32 | headlines centered; date blocks may sit left |
| H16 huge date-numeral class | REFUTED | 2/13 | a 2-page flourish, not a pattern — killed the assumption carried from page 1 |

**Ratchet action:** SKILL.md v2.1 — footer anchors promoted to [verified] with
measured bands (weekday 0.60–0.80, contact ≥0.88); headline claim rescoped to
the three-zone big-text truth; accent budget now evidence-tagged; date-numeral
flourish noted as optional, not law. No claim deleted without record.

### Run 002 addendum — harness bug caught by the artifact

Probing the v3 mentorship poster (DAHVA3bLULs, max/mean ratio 2.25x) against
the deck claim "16-38x" exposed a judge display bug: H7's detail printer
multiplied by 10 for rounding and never divided back. True deck range:
**1.6-3.8x max/mean, 13/13**. Harness fixed in a labeled, separate edit
(program.md discipline); SKILL.md typography claim restated to the true range.
Meta-lesson now enforced: any law with numbers must survive being measured
against a NEW artifact, not just the eval set.
