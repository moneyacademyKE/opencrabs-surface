# program.md — the research loop for this skill

You are the research agent for the `visual-taste` skill. Your job is to keep
every claim in SKILL.md honest by measurement. The deck is the eval set; the
harness is the judge; the skill is the artifact under iteration.

## The files and their owners

- **`scripts/taste_probe.swift`, `scripts/score_claims.clj`** — the immutable
  evaluator. You do NOT modify these to make a claim pass. If the harness is
  wrong, fix the harness in a separate, explicitly-labeled commit — never in
  the same change as a skill edit.
- **`SKILL.md`** — the file under iteration. Every edit must cite its evidence.
- **`EXPERIMENTS.md`** — the audit trail. Append, never rewrite history.

## The loop

1. Read SKILL.md. Extract any claim without an evidence tag, or any tag whose
   eval set has grown (new posters shipped since last run).
2. Get the eval set: export `DAFj1rOFszE` (or its successor) to PNGs.
3. Run the harness: `taste_probe.swift` per page → results.jsonl →
   `score_claims.clj` → verdict table.
4. Ratchet:
   - CONFIRMED → keep claim, update support count.
   - REFUTED → rewrite the claim to the measured truth, or demote it to
     [rule] with the compliance score stated. Never silently delete — the
     EXPERIMENTS log records why it changed.
   - New pattern observed → add as hypothesis, tag [dominant k/n] with the
     measured support, never as law on first sight.
5. Simplification beats addition: a run that deletes a vague claim while
   keeping the posters correct is a win. A tiny precision gain that adds ugly
   complexity is not.
6. Append the run to EXPERIMENTS.md.

## The metric

**Claim survival rate** = tagged claims that pass re-measurement / total tagged
claims. Target: 1.0. A skill at 1.0 says nothing it cannot prove.

## Hard constraints

- NEVER ship an unmeasured claim as [verified]. First sight = [dominant k/n].
- NEVER edit the deck to make it pass — the deck is ground truth, sins included.
- NEVER STOP mid-loop with an untested claim left tagged as verified. If the
  harness can't test it, demote the tag, don't leave the lie.
