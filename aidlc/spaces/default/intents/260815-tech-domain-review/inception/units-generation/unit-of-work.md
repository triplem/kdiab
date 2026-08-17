# Units of Work — Technology & Domain Review

**Stage:** units-generation (2.7) · **Intent:** technology & domain review (recommendations-only).
**Upstream inputs:** Application Design (`components.md`, `component-methods.md`, `services.md`,
`component-dependency.md`, `decisions.md`), `requirements.md`, `stories.md`.

> These units are **review work packages** — each produces part of the `docs/review/` deliverable set (and,
> deferred, GitHub issues), not deployable software. Boundaries follow the five theme workstreams + a shared
> foundational unit + four deliverable-assembly units (Q1=A, Q2=A, Q3=C, Q4=A). This artifact defines *what
> each unit owns*; the dependency topology is in `unit-of-work-dependency.md`; build order is decided later
> in Delivery Planning (2.8), not here.

## Unit Catalogue

### U0 — review-foundations (foundational)

- **Description:** the shared contract every theme workstream reuses — the `FindingRecord` schema (C1), the
  evidence-link format (`path/File.kt#symbol`, Q8=B), and the `EvidenceLedger` live-verification guard (C7).
- **Owns:** the finding schema + ID scheme (`FIND-<AREA>-NNN`), severity scale (Critical/High/Medium/Low,
  Critical=patient-safety), effort scale (S/M/L), the evidence-citation format, and the mandatory
  live-verify procedure for codekb-tracked anchors (#1082 closed, `vite.config.ts lines:72` ADR-015 floor,
  #894–#898 closed).
- **Deployment/shipping model:** a short `docs/review/README.md`-adjacent conventions note (or a section of
  the backlog doc); committed once, independently shippable.
- **Complexity:** S. **Notes:** this is the dependency root — no findings are authored until the schema and
  evidence rules exist, so it is built first (topology, not a build-order recommendation).

### U1 — clinical-dose-review (the MVR floor)

- **Description:** review `kdiab-calc` dose calculation vs. a reference model — bolus formula, IOB,
  ISF/correction, carb-ratio, unit handling (mg/dL vs mmol/L), rounding/guardrails (US-1, FR-1.1).
- **Owns:** `docs/review/clinical-safety.md` dose-calculation findings; each of the six dose dimensions gets
  an evidence-linked finding or an explicit "no concern found" with a citation and patient-safety impact.
- **Shipping model:** independently committable; one branch/issue.
- **Complexity:** L. **Notes:** the **single non-trimmable deliverable (FR-1.4)** — survives any capacity
  cut ahead of every other unit. Depends only on U0.

### U2 — clinical-guardrails-metrics-review

- **Description:** review `kdiab-treatments` safety guardrails (implausible-dose limits, correction-bolus
  stacking) and the TIR/AGP/HbA1c(GMI) metric definitions in `kdiab-analyze` (US-2, FR-1.2a/1.2b).
- **Owns:** the guardrails + metric-definition findings in `docs/review/clinical-safety.md`.
- **Shipping model:** independently committable. **Complexity:** M. Depends only on U0.

### U3 — data-model-review

- **Description:** assess measures/treatments/carbs/profiles schema completeness vs. real T1D workflows —
  extended/dual-wave bolus, temp basal, carb absorption, sensor calibration, exercise/illness context
  (US-3, FR-1.5).
- **Owns:** `docs/review/data-model.md`; each of the four schemas carries an explicit completeness verdict
  citing the schema/entity + patient-safety impact of any gap.
- **Shipping model:** independently committable. **Complexity:** M. Depends only on U0.

### U4 — security-review

- **Description:** GDPR special-category handling, auth hardening (Keycloak/OIDC/ABAC `canAccess`), and
  MDR/SaMD posture — flag obligations, do not certify (US-4, FR-2.1/2.2).
- **Owns:** `docs/review/security.md`. **Shipping model:** independently committable. **Complexity:** M.
  Depends only on U0.

### U5 — tech-debt-review

- **Description:** quantify the real test pyramid + Kover coverage, Detekt baseline debt per module, and
  cross-service duplication; **re-verify codekb-tracked anchors against the live repo** before reporting
  (US-5, FR-3.1/3.2). Do not report a resolved gap as open.
- **Owns:** `docs/review/tech-debt.md`. **Shipping model:** independently committable. **Complexity:** M.
  Depends on U0 (and uses U0's live-verify guard heavily).

### U6 — modernization-review

- **Description:** stack currency/deprecations, the nine-service boundary tension, CI/CD & release health,
  observability — each rewrite proposal paired with an incremental alternative (US-6, FR-4.1, C-1).
- **Owns:** `docs/review/modernization.md`. **Shipping model:** independently committable. **Complexity:** M.
  Depends only on U0.

### U7 — backlog-assembly (+ README index)

- **Description:** aggregate every theme finding into the prioritized `docs/review/BACKLOG.md` (value-density,
  clinical-safety first) and produce the `docs/review/README.md` navigation index (folded here — see the
  decomposition note; US-7, FR-D.1). Applies the C5.bandOf() authority to stamp each finding's roadmap phase.
- **Owns:** `BACKLOG.md` + `README.md`. **Shipping model:** independently committable. **Complexity:** M.
  Depends on U1–U6 (needs all findings).

### U8 — quick-wins

- **Description:** filter the findings to an explicit quick-wins list (effort=S, high value, independently
  shippable) → `docs/review/QUICK-WINS.md` (US-8, FR-D.2).
- **Owns:** `QUICK-WINS.md`. **Shipping model:** independently committable. **Complexity:** S.
- **Notes:** depends on the theme findings (U1–U6) directly, **not** on the assembled backlog — honouring
  US-8's INVEST independence from US-7 (see the dependency artifact for the rationale).

### U9 — roadmap

- **Description:** sequence the backlog into Near/Mid/Long value-density bands with rough effort per phase
  → `docs/review/ROADMAP.md` (US-9, FR-D.3).
- **Owns:** `ROADMAP.md`. **Shipping model:** independently committable. **Complexity:** S. Depends on U7.

### U10 — issue-materialization (deferred)

- **Description:** materialize the backlog as GitHub issues — one epic + native sub-issue per item, labelled
  `area:*`+`severity:*` (reuse-first), dedup against already-tracked items, `gh`-unavailable fallback queue
  (US-7 issues half, FR-D.1/D.5, A-2).
- **Owns:** the GitHub issue set (or the queued follow-up list). **Shipping model:** issues, not docs —
  **execution deferred** per the end-of-Inception park (RA-Q3=A; see `decisions.md` ADR-RVW-005). **Complexity:** S.
  Depends on U7.

## Complexity & Coverage Summary

| Unit | Complexity | Story | Priority (from stories.md) |
|------|:---:|---|---|
| U0 review-foundations | S | cross-cut | enabler |
| U1 clinical-dose-review | L | US-1 | Must (non-trimmable floor) |
| U2 clinical-guardrails-metrics | M | US-2 | Must |
| U3 data-model-review | M | US-3 | Must |
| U4 security-review | M | US-4 | Should |
| U5 tech-debt-review | M | US-5 | Should |
| U6 modernization-review | M | US-6 | Could |
| U7 backlog-assembly | M | US-7 | Must |
| U8 quick-wins | S | US-8 | Must |
| U9 roadmap | S | US-9 | Should |
| U10 issue-materialization | S | US-7 | Must (deferred exec) |

Every unit maps to at least one story and every non-Won't story (US-1…US-9) has an owning unit — full
coverage is verified in `unit-of-work-story-map.md`. The `team-practices.md` shipping rule (each unit
independently shippable in a burst, NFR-2) holds for all eleven; U10's shipping surface is GitHub issues,
deferred.

---

## Review

**Verdict:** READY

**Reviewer:** aidlc-architecture-reviewer-agent (independent sub-agent) — 2026-08-16T14:59:29Z. The reviewer completed all
four required checks; its full findings are recorded below (the sub-agent process was stopped just before it
self-appended this section, so its verdict is transcribed here verbatim in substance).

### Check 1 — DAG validity: PASS
11 declared units (U0–U10). Every `depends_on` resolves to a declared unit; topological layering
L0={U0} → L1={U1–U6} → L2={U7,U8} → L3={U9,U10} with no back-edges. Acyclic. The Mermaid diagram, YAML edge
block, and text fallback are mutually consistent.

### Check 2 — Story coverage (bidirectional): PASS
Every non-Won't story US-1…US-9 maps to a unit (US-7 → U7 docs + U10 issues); every unit has a story or is a
declared enabler (U0 = NFR-1/US-5 cross-cut enabler). US-10 (Won't) intentionally unassigned. No orphan
stories, no storyless non-enabler units.

### Check 3 — U8 divergence justified + internally consistent: PASS
Modelling `quick-wins` (U8) as depending on the six theme units rather than on `backlog-assembly` (U7) is
justified against `stories.md`'s explicit "US-8 is independent" statement (it privileges the authoritative
story dependency over the design's C4→C3 edge). All three artifacts model U8 → theme units consistently; no
residual U7→U8 or U8→U7 edge anywhere.
- *Minor non-blocking nit:* the dependency artifact says the refinement is "recorded in the stage diary"; the
  reviewer does not read memory.md, so could not verify that pointer — but the rationale is fully
  self-contained in the dependency artifact, so the claim is not load-bearing. (The diary entry does exist.)

### Check 4 — No build-order / critical-path language: PASS
No artifact prescribes a cross-unit build order or names a critical path. Sequencing words ("built first",
"index last") are each self-flagged as topology or within-unit ordering, or explicitly deferred to Delivery
Planning (2.8). The "Parallel Development Opportunities" section describes topological freedom, not a chosen
order.

### Sensor results (independently re-verified by the builder)
- **required-sections:** PASS — unit-of-work.md 2 H2, unit-of-work-dependency.md 5 H2, unit-of-work-story-map.md
  4 H2; the YAML edge block is present, well-formed, and DFS-verified acyclic (11 units).
- **upstream-coverage:** PASS — all seven consumed artefacts (components, component-methods, services,
  component-dependency, decisions, requirements, stories) are referenced in each output.
