# Design Decisions (ADRs) — Review Deliverable System

**Stage:** application-design (2.6) · **Intent:** technology & domain review (recommendations-only).
**Upstream inputs:** `requirements.md`, `stories.md`, `team-practices.md`, codekb `architecture.md` +
`component-inventory.md`.

> These are **design-record ADRs for the review deliverable system** — decisions about how the review is
> structured and produced. They are numbered `ADR-RVW-NNN` to avoid colliding with the kdiab platform
> ADRs (`ADR-NNN`) and service ADRs (`ADR-{SVC}-NNN`) in the project's `github-issue-management.md`
> convention. The review's *findings* may themselves recommend real platform/service ADRs — those are
> content, not these design decisions. Each ADR follows the inception-phase guardrail: Context, Decision,
> Consequences, Alternatives Rejected, plus a reversibility note.

---

## ADR-RVW-001 — Materialize deliverables as both `docs/review/` markdown and GitHub issues

- **Context:** FR-D.1 requires the prioritized backlog to exist as **both** committed markdown under a
  pinned repo path **and** actual GitHub issues, so findings are both browsable and trackable. A-2 warns
  `gh` may be unavailable.
- **Decision:** produce markdown docs under `docs/review/` (path pinned, resolving OQ-3) as the
  value-bearing primary; project them onto GitHub issues as a secondary, with a queued-list fallback if
  `gh` is unavailable (FR-D.1 fallback). Docs are never blocked by a tooling gap.
- **Consequences:** two output surfaces to keep consistent; the backlog doc is the source of truth and
  the issues are a derived projection (C6), so drift is one-directional and re-runnable. Solo maintainer
  gets a browsable review immediately, issues when tooling allows.
- **Alternatives Rejected:** *docs-only* (fails FR-D.1's tracking requirement); *issues-only* (not
  browsable as a coherent document, and loses the `docs/review/` durable record); *external tracker*
  (contradicts the team's GitHub-Issues-only practice in `team-practices.md`).
- **Reversibility:** High — dropping the issue projection later leaves the docs intact.

## ADR-RVW-002 — Per-theme document set + three cross-cutting docs + README index (Q1=A)

- **Context:** the review spans 5 assessment areas (clinical-safety, data-model, security, tech-debt,
  modernization) plus 3 cross-cutting deliverables (backlog, quick-wins, roadmap). Structure must be
  readable by a single non-committee maintainer (NFR-4) and let a capacity cut drop whole areas cleanly
  (FR-1.4).
- **Decision:** one markdown doc per theme (`clinical-safety.md`, `data-model.md`, `security.md`,
  `tech-debt.md`, `modernization.md`) + `BACKLOG.md` + `QUICK-WINS.md` + `ROADMAP.md` + `README.md` index.
- **Consequences:** each theme is independently reviewable and cuttable (maps to INVEST independence in
  `stories.md`); the backlog aggregates across themes, so a finding appears once in its theme doc and once
  in the backlog (by reference, not copy). More files than a single doc, but far more navigable.
- **Alternatives Rejected:** *single consolidated `REVIEW.md`* (harder to cut a theme, one giant file);
  *backlog-master only* (loses per-theme narrative and evidence context); *one-file-per-finding* (file
  sprawl, poor narrative for the solo reader).
- **Reversibility:** High — docs can be concatenated or split later without touching findings.

## ADR-RVW-003 — Finding Record schema: mandated fields + effort/phase/confidence (Q2=A,B,C)

- **Context:** every deliverable reads the same finding. NFR-1 mandates evidence-linkage; FR-1.3 mandates
  patient-safety impact; C-1 mandates an incremental alternative for rewrites; FR-D.5 mandates a
  cross-reference for already-tracked items. The team confirmed S/M/L estimates.
- **Decision:** a canonical `FindingRecord` (C1) with always-present mandated fields
  (`id, area, severity, evidence-link, recommendation, patient-safety-impact, incremental-alternative,
  cross-reference`) plus three optional fields kept this run: `effort` (S/M/L), `roadmap-phase`, and
  `confidence`. `id` scheme `FIND-<AREA>-NNN`.
- **Consequences:** one schema powers backlog, quick-wins, roadmap, and issues — no divergent shapes.
  `confidence` operationalizes the ideation guardrail to label uncertain claims. `validate()` turns the
  mandates into hard admission checks (a finding without evidence is a defect, not a warning).
- **Alternatives Rejected:** *mandated-fields-only* (no effort → weaker quick-wins/roadmap; no confidence
  → uncertainty hidden); *free-form findings* (fails NFR-1's enforceability and cross-doc consistency).
- **Reversibility:** Medium — adding a field later is cheap; removing `evidence-link` is not (it is the
  review's core invariant).

## ADR-RVW-004 — Severity scale Critical/High/Medium/Low, Critical reserved for patient-safety (Q3=A)

- **Context:** clinical safety is strictly first (NFR-3, FR-1.4). Severity must make the safety-first
  ordering legible at a glance and drive both backlog order and issue labels.
- **Decision:** 4-tier `Critical / High / Medium / Low`. **Critical is reserved** for patient-safety
  findings (dose calculation, treatment guardrails, clinical metric correctness). Non-safety findings
  cap at High.
- **Consequences:** the backlog's safety-first rule (ADR-RVW-006) becomes "all Critical first" — simple
  and auditable. A reviewer cannot accidentally rank a modernization item at Critical. Maps cleanly onto
  `severity:*` GitHub labels.
- **Alternatives Rejected:** *3-tier High/Medium/Low* (no dedicated safety band — safety findings blur
  into ordinary High); *5-tier with a Safety band above Critical* (extra tier with little gain once
  Critical is already reserved for safety).
- **Reversibility:** High — a relabel is mechanical.

## ADR-RVW-005 — GitHub issues: epic + native sub-issues; reuse-first labels; dedup; deferred (Q4=A, Q5=B)

- **Context:** FR-D.1 requires labelled issues per backlog item; FR-D.5 forbids duplicating already-tracked
  items; `team-practices.md` + `github-issue-management.md` prescribe native sub-issues (`addSubIssue`),
  no-assignee-at-creation, and issue reuse. A-2 warns `gh` may be unavailable. RA-Q3=A parks execution.
- **Decision:** one epic "Tech & Domain Review" + one **native sub-issue per backlog item** labelled
  `area:*` + `severity:*`. Labels are **reuse-first**: reuse existing repo labels where they fit, create
  the missing `area:*` / `severity:*` / `quick-win` / `review` set. Already-tracked items are
  cross-referenced, never re-filed. **Execution is deferred** (designed now, run later); the fallback
  queue applies if `gh` is unavailable.
- **Consequences:** the epic gives a live progress tracker; sub-issues stay individually shippable
  (NFR-2). Reuse-first avoids label sprawl but in practice the `area:*` labels won't pre-exist and will be
  created. Dedup honours the project "reuse issues, don't duplicate" rule. Because execution is deferred,
  nothing is created against GitHub in this run.
- **Reconciliation with FR-D.1 (explicit — reviewer finding):** FR-D.1 / RA-Q1=C literally require issues
  "opened **in this run**", with a pass/fail of "a corresponding GitHub issue exists per backlog item".
  This ADR **knowingly overrides that "in this run" clause** on the strength of RA-Q3=A (the intent parks
  at end of Inception, so the Construction-flavoured act of creating issues does not execute this run). The
  override is scoped and traceable, not silent: the backlog remains the value-bearing deliverable, and the
  `fallbackQueue()` follow-up list (the same mechanism A-2 defines) records the exact issue set that will
  be opened when the maintainer un-parks (OQ-1). A downstream reader checking FR-D.1's pass/fail should
  read it as **"satisfied by a queued, ready-to-open issue set"** for this parked run, with issue creation
  gated on the end-of-Inception continue decision. This is a divergence from the literal criterion, named
  here so it is not mistaken for an unmet requirement.
- **Alternatives Rejected:** *flat issues, no epic* (loses the progress tracker the repo rule wants);
  *one issue per theme* (too coarse — a theme issue isn't independently shippable); *dedicated fresh
  label set ignoring existing labels* (label sprawl, rejected in favour of reuse-first).
- **Reversibility:** High for labels/topology; the deferral means no irreversible external writes occur now.

## ADR-RVW-006 — Value-density prioritization with a single phase-tag source of truth (Q6=A, Q7=A)

- **Context:** NFR-3 orders the backlog by value-density with clinical safety first; FR-D.3 wants a
  Near/Mid/Long roadmap; Q2 keeps a per-finding `roadmap-phase` tag. A naive design would let the
  per-finding tag and the roadmap band drift apart.
- **Decision:** the `PrioritizedBacklog` (C3) orders by `(safetyRank, valueDensity desc, effort asc)` with
  effort on the T-shirt S/M/L scale (S≈1 burst, M≈2–3d, L≈~5d). The `PhasedRoadmap.bandOf()` (C5) is the
  **single authority** that assigns each finding's `roadmap-phase`; C3 stamps the field by calling it, and
  C5 groups by the same result — so tag and band are identical by construction. Bands: Near = quick-wins +
  Must clinical-safety; Mid = Should security + tech-debt; Long = Could modernization.
- **Consequences:** backlog, quick-wins, and roadmap cannot disagree about which phase an item is in;
  regenerating any one from the backlog is safe. Effort is shown per item but does not override
  value-density ordering (a cheap low-value item never jumps a costly safety item).
- **Alternatives Rejected:** *roadmap grouped by theme* (buries safety-first ordering); *grouped by
  ascending effort* (optimizes for cheapness over value — contradicts NFR-3); *two independent phase
  sources* (drift risk — the exact problem this ADR removes).
- **Reversibility:** High — the band rule is one function; re-banding is a re-run.

## ADR-RVW-007 — Evidence links as path + symbol, with a mandatory live-verification guard (Q8=B, US-5)

- **Context:** NFR-1 requires every finding to be evidence-linked; line numbers drift as `main` moves; and
  US-5 + the `project.md` learned rule require re-verifying codekb-tracked anchors against the live repo
  (several are already resolved: #1082 closed, `vite.config.ts lines:72` is an ADR-015 intentional floor,
  #894–#898 closed).
- **Decision:** the canonical citation is `path/File.kt` + `#symbolName` (no line number) — stable across
  edits. The `EvidenceLedger` (C7) `verifyLive()` is a **mandatory gate** before any codekb-tracked anchor
  is reported; a resolved anchor must not be reported as an open gap, and an unverifiable anchor is marked
  `confidence=Low` for manual re-check.
- **Consequences:** citations survive routine refactors; the review can't regress into reporting
  already-fixed debt as open (the exact failure the learned rule guards against). Symbol-based links need
  a symbol lookup rather than a line jump, a minor navigation trade-off accepted for durability.
- **Alternatives Rejected:** *path + line pinned to commit `d6c8866b`* (precise but line refs rot and a
  pinned commit hides that `main` has since moved on/resolved items); *file-path only* (too coarse to be
  verifiable evidence under NFR-1).
- **Reversibility:** High — link format is mechanical to change; the live-verify guard is a durable
  practice worth keeping regardless of format.

---

## Trade-off & Reversibility Summary

| ADR | Decision | Reversibility | Key trade-off |
|---|---|---|---|
| RVW-001 | docs + issues, docs primary | High | two surfaces vs. tracking + browsability |
| RVW-002 | per-theme + 3 cross-cutting + README | High | more files vs. navigability + cuttability |
| RVW-003 | canonical FindingRecord schema | Medium | schema discipline vs. authoring freedom |
| RVW-004 | 4-tier severity, Critical=safety | High | fixed band vs. flexibility |
| RVW-005 | epic + sub-issues, reuse-first, deferred | High | topology setup vs. progress tracking |
| RVW-006 | value-density + single phase authority | High | one ordering rule vs. per-doc autonomy |
| RVW-007 | path+symbol + live-verify guard | High | symbol lookup vs. durable, accurate evidence |

All seven decisions are reversible without discarding findings, consistent with the architect principle
"reversibility over perfection." No decision writes anything irreversible to an external system in this
run — issue creation (the only outward action) is deferred per RA-Q3=A. Every decision traces to a
requirement in `requirements.md`, a story in `stories.md`, or a practice in `team-practices.md`.
