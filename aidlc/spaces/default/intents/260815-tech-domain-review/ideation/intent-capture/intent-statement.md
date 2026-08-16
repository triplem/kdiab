# Intent Statement — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Scope:** enterprise (comprehensive depth) — user-confirmed
**Initiative type:** Assessment / advisory (produces prioritized improvement recommendations, not a shipped feature)
**Date:** 2026-08-15

---

## Problem Statement

The **kdiab** T1D (Type 1 Diabetes) management platform — a nine-component Kotlin/Ktor + React 19
monorepo — has grown organically as a solo-maintained project. There is no current systematic,
evidence-based assessment of:

- its **technology health** (stack currency, service boundaries, the test pyramid, static-analysis
  debt, code duplication, CI/CD, observability), and
- its **T1D domain correctness** (dose calculation, basal profiles, CGM/BGM interpretation, safety
  guardrails, and data-model completeness against real T1D workflows).

Without a prioritized view of where the platform is strong versus weak, improvement effort risks
being ad hoc. This initiative reviews **both** dimensions and produces a prioritized, actionable
improvement plan the maintainer can execute incrementally.

## Target Customer

- **Primary:** the **solo maintainer** (you) — the sole decision-maker, implementer, and audience for
  the review output (Q3 = A). The deliverables must serve one person's "where do I invest next"
  decision, not a formal committee.
- **Indirect beneficiaries:** the platform's end users — T1D patients and their doctors — whose safety
  depends on the clinical-correctness and safety-guardrail findings the review prioritizes.
- **Future (non-imminent):** potential collaborators; trigger is a periodic health-check, not an
  onboarding push (Q4 = D), so onboarding-readiness is a secondary lens, not the driver.

## Success Metrics (measurable)

Derived from Q8 (A, D, C). The review is successful when it produces:

| Deliverable | Measure of done |
|---|---|
| **Prioritized improvement backlog** (GitHub issues) | Every one of the 5 review areas represented; each issue actionable, labelled by area + severity, and linked to evidence |
| **Quick wins** ready to implement now | A short, explicit list the maintainer can act on immediately |
| **Phased improvement roadmap** | Near / mid / long-term phases, each with a rationale and rough effort |

Every finding must be **evidence-linked** — a detekt finding, a coverage gap, a duplication cluster, a
concrete domain-correctness concern — not opinion.

## Initiative Trigger

**Periodic health-check / curiosity** (Q4 = D). No external deadline, incident, or regulatory clock.
Implications: the review can be thorough (no timeline pressure) but must stay pragmatic given
**solo-maintainer capacity** — recommendations should be sequenced so one person can chip away at them.

## Review Criteria (what "review" concretely means here)

**Breadth — all areas (Q1 = A–E)**, with explicit attention (Q1 free-text) to:
- the **test pyramid** (unit/integration/e2e balance, Kover coverage floors),
- **static-analysis issues** (detekt findings and baseline debt),
- **code duplication** across the nine services.

**Goal priority (Q2):**
1. **Clinical correctness & safety** (C) — highest.
2. **Security & compliance** for health data / PII (D).
3. Technical-debt reduction & maintainability (A) — next.
4. Stack modernization (B) — next.
5. Performance & scalability (E) — explicitly deprioritized.

**Technology dimensions — all (Q5 = A–E):** stack currency, architecture/service boundaries, testing,
CI/CD & release, observability/operability.

**Domain dimensions (Q6 = A, B, C):** clinical correctness, safety guardrails, data-model completeness.
Interoperability (Nightscout/AAPS/xDrip+) and terminology/standards alignment (HL7/FHIR) are **out of
priority** for this review (not selected).

**Constraints (Q7 = E):** none. Recommendations may propose anything up to and including rewrites; no
"must stay on X" fences. (The maintainer's own project rules — e.g. self-hostable, Kotlin/React — still
inform pragmatism, but are not hard limits for *proposals*.)

## Initial Scope Signal

- The intent is **assessment-first**. Under AI-DLC, the value-bearing work lands in **Ideation +
  Inception** (feasibility, requirements-analysis, application-design) — that is where the review and
  its recommendations are produced.
- **Output mode = recommendations only (Q9 = A):** implementation is deferred to the maintainer's later
  choice. Consequently the **Construction & Operation** stages of the confirmed **enterprise** scope
  carry **no build mandate** for this intent.
- **Recommended process adjustment:** treat **end of Inception as the natural park/finish point** for
  this review. If, after seeing the recommendations, you decide to implement, resume Construction — or
  re-scope then. Enterprise was confirmed knowingly; this note simply marks where the review's value is
  complete so the remaining stages aren't run as busywork.

## Open Questions / To Revisit

- At the end of Inception: decide whether to park (recommendations delivered) or continue into
  Construction to implement selected quick wins.
- Performance/scalability (Q2 = E, deprioritized) and interoperability/standards (Q6 = D/E, not
  selected) are intentionally light — confirm they can stay out of scope as findings surface.
