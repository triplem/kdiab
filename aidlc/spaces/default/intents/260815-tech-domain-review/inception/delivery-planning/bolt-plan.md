# Bolt Plan — Technology & Domain Review

**Stage:** delivery-planning (2.8, capstone Inception) · **Intent:** technology & domain review (recommendations-only).
**Upstream inputs:** `requirements.md`, `stories.md`, `refined-mockups/mockups.md`, Application Design
`components.md`, `unit-of-work.md`, `unit-of-work-dependency.md`, `unit-of-work-story-map.md`, `team-practices.md`.

> The Bolt sequence is the **economic path** a solo maintainer follows through the 11-unit DAG to produce the
> review. Chosen heuristic: **value-first with the clinical-safety floor first** (Q1=A), **one unit per Bolt**
> (Q2=A, ~11 Bolts), **incremental/living backlog** (Q3=A), **issues last + deferred/gh-gated** (Q4=A).
> Execution is **deferred** — this plan is followed if/when the maintainer un-parks into Construction
> (RA-Q3=A, OQ-1). No Bolt is a walking skeleton (skipped per `team-practices.md`). Each Bolt is one
> maintainer burst, independently shippable (NFR-2). The demo for a review Bolt is the committed doc section
> (and, for U10, the issue set / queued list).

## Bolt Sequence

| # | Bolt | Unit(s) | Story | Priority | Definition of Done |
|---|------|---------|-------|----------|--------------------|
| B1 | review-foundations | U0 | cross-cut enabler | enabler | Finding schema, severity/effort scales, evidence-link format, and live-verify procedure documented; every later Bolt can author findings against them |
| B2 | clinical-dose-review | U1 | US-1 | Must (floor) | All six dose dimensions (bolus formula, IOB, ISF/correction, carb-ratio, unit handling, rounding) have an evidence-linked finding or explicit "no concern found" + patient-safety impact |
| B3 | clinical-guardrails-metrics | U2 | US-2 | Must | Implausible-dose limit, correction-bolus stacking, TIR, AGP, HbA1c/GMI each carry an evidence-linked verdict + safety impact |
| B4 | data-model-review | U3 | US-3 | Must | measures/treatments/carbs/profiles each carry a completeness verdict citing the schema/entity + safety impact of any gap |
| B5 | backlog-assembly (initial) | U7 | US-7 | Must | Prioritized `BACKLOG.md` over the Must themes (B2–B4) + `README.md` index exist; value-density order, clinical-safety first; living doc extended by later Bolts |
| B6 | quick-wins (initial) | U8 | US-8 | Must | `QUICK-WINS.md` with the effort=S high-value items from the Must-theme findings; living doc |
| B7 | security-review | U4 | US-4 | Should | GDPR/auth/MDR findings evidence-linked + obligations flagged; findings appended to the living backlog + quick-wins |
| B8 | tech-debt-review | U5 | US-5 | Should | Coverage/Detekt/duplication quantified with the live-verify guard applied (no resolved gap reported as open); appended to the living backlog |
| B9 | modernization-review | U6 | US-6 | Could | Stack/boundary/CI/observability findings, each rewrite paired with an incremental alternative; appended to the living backlog |
| B10 | roadmap | U9 | US-9 | Should | `ROADMAP.md` over the now-complete backlog: Near/Mid/Long value-density bands + rough effort per phase |
| B11 | issue-materialization | U10 | US-7 (issues) | Must (deferred) | Epic + native sub-issue per backlog item, `area:*`/`severity:*` labels (reuse-first), dedup; OR the queued fallback list. **Deferred + gh-gated.** |

## Per-Bolt Detail

### B1 — review-foundations (U0)
- **Confidence hypothesis:** shipping this proves the review has one consistent finding vocabulary — every
  subsequent finding is comparable, prioritizable, and evidence-linked by construction.
- **Demo:** the conventions note (schema + severity/effort/evidence rules) committed under `docs/review/`.
- **Walking skeleton?** No.

### B2 — clinical-dose-review (U1) — the MVR floor
- **Confidence hypothesis:** the maintainer can either trust `kdiab-calc`'s dosing or knows exactly which of
  the six dose dimensions has a concrete, evidence-linked safety concern. **This is the single non-trimmable
  deliverable (FR-1.4)** — if capacity collapses, B2 is the review.
- **Demo:** the dose-calculation section of `docs/review/clinical-safety.md`.

### B3 — clinical-guardrails-metrics (U2)
- **Confidence hypothesis:** stored treatment events can't silently exceed safe limits and displayed
  TIR/AGP/HbA1c can't mislead the patient or clinician — each with a code-referenced verdict.
- **Demo:** the guardrails + metrics section of `docs/review/clinical-safety.md`.

### B4 — data-model-review (U3)
- **Confidence hypothesis:** the maintainer knows precisely what real T1D workflows the schema can't yet
  represent (e.g. dual-wave bolus, temp basal, carb absorption) and the safety impact of each gap.
- **Demo:** `docs/review/data-model.md`.

### B5 — backlog-assembly initial (U7)
- **Confidence hypothesis:** after only the Must themes, the maintainer already has an actionable,
  value-ordered backlog + a navigable index — value delivered before the Should/Could work runs.
- **Demo:** `docs/review/BACKLOG.md` (Must-theme scope) + `docs/review/README.md`.
- **Note:** placing U7 here (after B2–B4, not after all six themes) is the deliberate DAG deviation — see
  `risk-and-sequencing-rationale.md`.

### B6 — quick-wins initial (U8)
- **Confidence hypothesis:** the maintainer has an immediately-actionable short list to make progress in one
  burst, drawn from the highest-value findings so far.
- **Demo:** `docs/review/QUICK-WINS.md`.

### B7 — security-review (U4)
- **Confidence hypothesis:** the maintainer understands their GDPR/auth/MDR exposure for T1D health data,
  with obligations flagged (not certified). Findings extend the living backlog.
- **Demo:** `docs/review/security.md` + backlog delta.

### B8 — tech-debt-review (U5)
- **Confidence hypothesis:** the highest-leverage cleanups are quantified (real coverage, Detekt baselines,
  duplication) with already-resolved items correctly excluded via the live-verify guard.
- **Demo:** `docs/review/tech-debt.md` + backlog delta.

### B9 — modernization-review (U6)
- **Confidence hypothesis:** modernization options are visible without over-engineering the self-hosted
  setup — every rewrite paired with an incremental alternative.
- **Demo:** `docs/review/modernization.md` + backlog delta.

### B10 — roadmap (U9)
- **Confidence hypothesis:** the complete backlog is sequenced into burst-sized Near/Mid/Long phases the
  maintainer can actually execute against occasional capacity.
- **Demo:** `docs/review/ROADMAP.md`.

### B11 — issue-materialization (U10) — deferred
- **Confidence hypothesis:** every backlog item is trackable as a labelled GitHub issue under one epic (or a
  ready-to-open queued list), with already-tracked items cross-referenced not duplicated.
- **Demo:** the epic + sub-issues (or the queued follow-up list in `BACKLOG.md`).
- **Gating:** runs only after the end-of-Inception continue decision (OQ-1) and only if `gh` is
  available/authorized (A-2). **Not executed this run.**

## DAG Conformance

All Bolt-to-Bolt orderings respect `unit-of-work-dependency.md` **except** the deliberate value-first
placement of B5/B6 (backlog + quick-wins over the Must themes) ahead of the Should/Could theme Bolts. That
single deviation is justified in `risk-and-sequencing-rationale.md`; the living-backlog mechanism (B7–B9
append) preserves eventual full coverage, and B10 (roadmap) still runs after every theme so it sequences the
complete backlog.
