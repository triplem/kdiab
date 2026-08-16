# Technology & Domain Review — Conventions

> **Unit U0 — review-foundations.** The shared contract every theme workstream reuses: the finding
> schema, the ID scheme, the severity/effort/confidence scales, the evidence-link format, and the
> mandatory live-verification procedure. No finding is authored until these rules exist — this is the
> dependency root of the whole review.
>
> Derives from Application Design ADR-RVW-003 (schema), ADR-RVW-004 (severity), ADR-RVW-006
> (prioritization + phase authority), ADR-RVW-007 (evidence links + live-verify). Recommendations-only:
> this review flags concerns with evidence and pairs any rewrite with an incremental alternative; it
> does not author corrected code or tests this run (requirements FR-1.1 depth, RA-Q2=A).

## Finding Record schema

Every finding — in a theme doc, in the backlog, and (deferred) as a GitHub issue — is one canonical
record. The mandated fields are always present; a finding missing a mandated field is a **defect**, not
a warning, and is not admitted to the backlog (NFR-1, `validate()` in application-design).

| Field | Required | Values / format | Source |
|---|---|---|---|
| `id` | yes | `FIND-<AREA>-NNN` (see area codes) | ADR-RVW-003 |
| `area` | yes | one of the five areas below | ADR-RVW-003 |
| `severity` | yes | Critical / High / Medium / Low | ADR-RVW-004 |
| `evidence-link` | yes | `path/File.kt#symbol` (see format) | ADR-RVW-007, NFR-1 |
| `recommendation` | yes | what to do (one actionable sentence) | FR-D.4 |
| `patient-safety-impact` | yes | impact statement; `n/a` for non-clinical | FR-1.3 |
| `incremental-alternative` | conditional | required **iff** the recommendation is a rewrite | C-1 |
| `cross-reference` | conditional | required **iff** already tracked (issue/ADR/TODO) | FR-D.5 |
| `effort` | optional (kept) | S / M / L | Q6=A |
| `roadmap-phase` | optional (kept) | Near / Mid / Long (assigned by roadmap authority) | Q7=A, ADR-RVW-006 |
| `confidence` | optional (kept) | High / Medium / Low (the review's own certainty) | Q2=C |

### Finding block format (theme docs)

Each finding is rendered as a fenced block so it reads the same in every theme doc and can be lifted
verbatim into the backlog:

```
#### FIND-CLIN-001 — <short title>
- Severity: High  ·  Effort: M  ·  Confidence: High  ·  Phase: Near
- Evidence: `kdiab-calc/.../DoseCalculationService.kt#calculateBolus`
- Patient-safety impact: <impact, or n/a>
- Finding: <what the evidence shows>
- Recommendation: <what to do>
- Incremental alternative: <required only if the recommendation is a rewrite>
- Cross-reference: <#issue / ADR-… / TODO — only if already tracked>
```

An area that yields no concern is **not** silently omitted — it is recorded as an explicit
`no concern found` finding with a citation, so completeness is auditable (per each theme story's
Given/When/Then and application-design `assess()` error handling).

## Area codes

The `id` scheme is `FIND-<AREA>-NNN`, `NNN` zero-padded and unique within an area.

| Area | Code | Theme doc |
|---|---|---|
| clinical-safety | `CLIN` | `clinical-safety.md` |
| data-model | `DATA` | `data-model.md` |
| security | `SEC` | `security.md` |
| tech-debt | `DEBT` | `tech-debt.md` |
| modernization | `MOD` | `modernization.md` |

## Severity scale

Four tiers. **Critical is reserved for patient-safety findings** (dose calculation, treatment
guardrails, clinical-metric correctness). A non-safety finding caps at High — a modernization or
tech-debt item can never be Critical (ADR-RVW-004). This makes the backlog's safety-first ordering
("all Critical first") legible at a glance and maps cleanly onto `severity:*` GitHub labels.

| Severity | Meaning |
|---|---|
| Critical | Patient-safety — incorrect dose, missing guardrail, or misleading clinical metric that could harm a patient. Reserved for clinical/domain findings. |
| High | Serious defect or risk (security exposure, correctness gap, material debt) with no direct patient-harm path. |
| Medium | Real but bounded issue; worth fixing, not urgent. |
| Low | Minor / cosmetic / nice-to-have. |

## Effort scale (T-shirt)

Effort is one maintainer's calendar estimate, not story points (Q6=A). It is shown per item but never
overrides value-density ordering — a cheap low-value item never jumps a costly safety item (ADR-RVW-006).

| Effort | Meaning |
|---|---|
| S | ~1-day burst — a single feature-branch-per-issue change. |
| M | 2–3 days. |
| L | multi-burst, ~5 days. |

## Confidence scale

The review's own certainty about a finding — operationalizes the ideation guardrail to label uncertain
claims rather than present speculation as fact.

| Confidence | Meaning |
|---|---|
| High | Directly evidenced in current code; verified against live `main`. |
| Medium | Strongly implied by evidence; some inference. |
| Low | Could not be fully verified against the live repo (e.g. anchor unreachable) — flagged for manual re-check; never reported as confirmed-open on stale evidence. |

## Evidence-link format & live-verification procedure

**Format (ADR-RVW-007):** the canonical citation is `path/File.kt` + `#symbolName` — **no line number**.
Line numbers rot as `main` moves; symbols survive routine refactors. Example:
`kdiab-calc/src/main/kotlin/org/javafreedom/kdiab/calc/application/service/DoseCalculationService.kt#calculateBolus`.
For a schema/migration cite the changelog file + changeSet id; for config cite the file + key.

**Live-verification (mandatory — US-5 currency guard, `project.md` learned rule):** every
codekb-tracked anchor MUST be re-checked against the **live repo** before it is reported. The RE codekb
is a point-in-time snapshot (commit `d6c8866b`) and goes stale. A resolved gap reported as open is a
defect. Procedure for any anchor drawn from the codekb (an issue number, a config line, a TODO):

1. Resolve the anchor's current state on live `main` — `gh issue view <n>` for issues; read the current
   file for a config/TODO anchor; check the referencing ADR.
2. Classify: `open` (still a real gap) · `resolved` (fixed/closed — do **not** report as open) ·
   `accepted-risk` (an intentional, documented decision — report as context, not as a defect).
3. If the anchor cannot be verified against the live repo, mark the finding `confidence=Low` and flag it
   for manual re-check — never assert confirmed-open on stale evidence.

**Known-resolved anchors (verified — do NOT report as open debt):**

| Anchor | Live status | Note |
|---|---|---|
| Issue #1082 (UI coverage) | resolved / closed | Superseded by the `vite.config.ts` ADR-015 floor below. |
| `kdiab-ui/vite.config.ts` coverage `lines:72` | accepted-risk | **Intentional** ADR-015 floor via exclusions — not an unmet 80% gap. |
| Issues #894–#898 (Nightscout v3 HISTORY) | resolved / closed | Do not re-file. |

## Backlog prioritization & phase authority

- **Ordering key:** `(safetyRank, valueDensity desc, effort asc)` — every clinical-safety Critical/High
  sorts ahead of everything else (NFR-3, FR-1.4 floor). Value-density = (clinical/safety weight +
  risk-reduction) per unit effort.
- **Single phase authority (ADR-RVW-006):** the roadmap band is the one source of truth for a finding's
  `roadmap-phase`; the backlog stamps the field from the same rule, so the per-finding tag and the
  roadmap grouping cannot drift. Bands: **Near** = quick-wins + Must clinical-safety · **Mid** = Should
  security + tech-debt · **Long** = Could modernization.

## Deliverable document set (ADR-RVW-002)

Committed under `docs/review/` (path pinned by FR-D.1, resolving OQ-3), each independently reviewable
and cuttable:

| Doc | Owner unit | Contents |
|---|---|---|
| `CONVENTIONS.md` | U0 | this file |
| `clinical-safety.md` | U1, U2 | dose calc + guardrails + metric-definition findings |
| `data-model.md` | U3 | schema-completeness findings |
| `security.md` | U4 | GDPR / auth / MDR posture findings |
| `tech-debt.md` | U5 | coverage / Detekt / duplication findings (live-verify applied) |
| `modernization.md` | U6 | stack / boundary / CI / observability findings |
| `BACKLOG.md` | U7 | prioritized master list + queued issue set (deferred) |
| `QUICK-WINS.md` | U8 | effort=S, high-value, independently shippable subset |
| `ROADMAP.md` | U9 | Near / Mid / Long value-density bands |
| `README.md` | U7 | navigation index + one-line reading guide (solo maintainer, NFR-4) |

## Practice conformance (NFR-5)

Every recommendation, when later implemented, MUST be expressible under `team-practices.md`: one feature
branch per issue, **merge-commit not squash** (preserves `Closes #N`), ≥80% coverage on new/changed
code, green CI before merge. No finding may imply a practice violation; the roadmap notes this so the
maintainer can act on any item as a single compliant change.
