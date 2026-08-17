# User Stories — Technology & Domain Review

Stories translate `requirements.md` into the consuming personas' voice (`personas.md`), broken down by
review theme + the three cross-cutting deliverables. Each is INVEST-compliant with Given/When/Then
acceptance criteria and a MoSCoW priority. Priorities feed Delivery Planning's MVP-boundary decision.
Every story respects `team-practices.md` (each finding independently shippable in a burst).

Legend — priority mirrors `requirements.md` value-density: clinical safety **Must** (non-negotiable);
deliverables **Must** for the backlog + quick-wins, **Should** for the roadmap (it depends on the
backlog); security/tech-debt **Should**; modernization **Could**; out-of-scope **Won't**.

---

## Theme 1 — Clinical Correctness, Safety & Data-Model Completeness (Must Have)

### US-1 — Dose-calculation correctness (traces FR-1.1)
**As** Sam, **I want** `kdiab-calc`'s dose calculation reviewed against a reference model (bolus formula,
IOB, ISF/correction, carb-ratio, unit handling, rounding), **so that** I can trust it won't recommend an
unsafe dose for Priya.
- **Given** the `kdiab-calc` implementation, **when** the review runs, **then** each of {bolus formula,
  IOB, ISF/correction, carb-ratio, unit handling, rounding} has an evidence-linked finding or an explicit
  "no concern found" with a code reference **and states the patient-safety impact** (FR-1.3).
- **Priority:** Must. **MVR floor (FR-1.4):** this is the single **non-trimmable** deliverable — if
  capacity forces a cut, US-1 survives ahead of every other story.
- **INVEST:** independent of other themes; testable via the pass/fail above.
- **Depends on:** RE codekb (evidence). **Benefit owner:** Priya (safety).

### US-2 — Guardrails & metric-definition correctness (traces FR-1.2a, FR-1.2b)
**As** Sam, **I want** treatments safety guardrails and the TIR/AGP/HbA1c(GMI) definitions in
`kdiab-analyze` verified, **so that** stored events and displayed metrics can't mislead Priya or Dr. Chen.
- **Given** `kdiab-treatments` and `kdiab-analyze`, **when** the review runs, **then** each of
  {implausible-dose limit, correction-bolus stacking, TIR, AGP, HbA1c/GMI} carries an evidence-linked
  verdict with a code reference **and states the patient-safety impact** (FR-1.3).
- **Priority:** Must. **INVEST:** small, independently testable. **Benefit owner:** Priya, Dr. Chen.

### US-3 — Data-model completeness (traces FR-1.5)
**As** Sam, **I want** the measures/treatments/carbs/profiles data model assessed against real T1D
workflows, **so that** I know what the schema can't yet represent (e.g. extended/dual-wave boluses, temp
basals, carb absorption, sensor calibration).
- **Given** the four service schemas, **when** the review runs, **then** each carries an explicit
  completeness verdict citing the schema/entity **and states the patient-safety impact** of any gap (FR-1.3).
- **Priority:** Must. **INVEST:** valuable, estimable. **Benefit owner:** Priya, Dr. Chen.

## Theme 2 — Security & Compliance (Should Have)

### US-4 — Security & regulatory posture (traces FR-2.1, FR-2.2)
**As** Sam, **I want** GDPR special-category handling, auth hardening (Keycloak/OIDC/ABAC), and MDR/SaMD
posture reviewed, **so that** I understand my regulatory exposure for T1D health data.
- **Given** the auth plumbing and data model, **when** the review runs, **then** GDPR/auth/MDR findings
  are evidence-linked and regulatory obligations are *flagged* (not executed).
- **Priority:** Should. **INVEST:** independent; testable via evidence-linkage.

## Theme 3 — Tech Debt / Code Health (Should Have)

### US-5 — Test pyramid, coverage & static-analysis debt (traces FR-3.1, FR-3.2)
**As** Sam, **I want** the real test-pyramid + Kover coverage picture, Detekt baseline debt, and
cross-service duplication quantified, **so that** I can target the highest-leverage cleanups within a burst.
- **Given** the RE `code-quality-assessment.md`, **when** the review runs, **then** coverage gaps
  (nightscout 0 e2e confirmed current), Detekt baseline counts, and duplication clusters are quantified
  and evidence-linked, cross-referencing existing issues rather than duplicating them.
- **Evidence-currency guard:** the review MUST re-verify codekb-tracked anchors against live repo state
  before reporting them — some are already resolved (issue **#1082 is closed** and `kdiab-ui/vite.config.ts`
  sets `lines: 72` per ADR-015, an intentional exclusion-based floor, **not** an unmet 80% gap; Nightscout
  v3 HISTORY **#894–#898 are closed**). Do NOT report a resolved gap as open.
- **Priority:** Should. **INVEST:** independently shippable per cleanup.

## Theme 4 — Modernization / Architecture (Could Have)

### US-6 — Stack currency, boundaries, CI/CD & observability (traces FR-4.1)
**As** Sam, **I want** stack currency, the nine-service boundary tension, CI/CD & release health, and
observability assessed, **so that** I can plan modernization without over-engineering the self-hosted setup.
- **Given** the RE `architecture.md` and `code-structure.md`, **when** the review runs, **then** each
  modernization dimension has an evidence-linked finding with an incremental option (rewrites paired with
  an incremental alternative per C-1).
- **Priority:** Could. **INVEST:** negotiable scope.

## Deliverables (cross-cutting — backlog + quick-wins Must; roadmap Should)

### US-7 — Prioritized evidence-linked backlog (traces FR-D.1, FR-D.4, FR-D.5)
**As** Sam, **I want** a prioritized, evidence-linked backlog materialized as **both** `docs/review/`
markdown **and** labelled GitHub issues, **so that** every finding is actionable and traceable.
- **Given** all theme findings, **when** deliverables are produced, **then** a `docs/review/` backlog doc
  exists AND a corresponding labelled (area+severity) GitHub issue exists per item; codekb-tracked items
  are cross-referenced, not re-filed; if `gh` is unavailable, docs ship and issues queue.
- **Priority:** Must. **INVEST:** testable via the pass/fail. **Depends on:** US-1…US-6.

### US-8 — Quick-wins list (traces FR-D.2)
**As** Sam, **I want** an explicit quick-wins list, **so that** I can make immediate progress in one burst.
- **Given** the findings, **when** deliverables are produced, **then** a short, explicitly-labelled
  quick-wins list exists, each item independently shippable. **Priority:** Must.

### US-9 — Phased roadmap (traces FR-D.3)
**As** Sam, **I want** a near/mid/long-term roadmap with rough effort per phase, **so that** I can
sequence work to my occasional-burst capacity.
- **Given** the prioritized backlog, **when** deliverables are produced, **then** a phased roadmap exists
  with a rationale + rough effort per phase. **Priority:** Should. **Depends on:** US-7.

## Out of Scope (Won't Have — this run)

### US-10 — Deferred implementation & out-of-scope areas
**As** Sam, **I won't** implement recommendations in this run (park at end of Inception, RA-Q3=A), and the
review **won't** cover performance/scalability (intent Q2=E, deprioritized) or interoperability/standards
(intent Q6 D/E, not selected).
- **Priority:** Won't. **Rationale:** preserves value-density focus; revisit at the end-of-Inception
  park/continue decision (OQ-1).

---

## Story Dependencies & INVEST Summary

- **Independence:** US-1…US-6 (theme reviews) are mutually independent; US-7 depends on US-1…US-6;
  US-9 depends on US-7. US-8 is independent (draws from whatever findings exist).
- **Testability:** every non-Won't story has a Given/When/Then with an evidence-linkage pass/fail.
- **MVP signal for Delivery Planning:** US-1, US-2, US-3, US-7, US-8 are the Must-Have core (clinical
  safety + the actionable backlog/quick-wins); US-4, US-5, US-9 Should; US-6 Could; US-10 Won't.
  **US-1 is the non-trimmable floor (FR-1.4)** — it must survive any capacity cut ahead of all others.
