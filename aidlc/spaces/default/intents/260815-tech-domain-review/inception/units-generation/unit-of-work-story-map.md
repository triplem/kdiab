# Unit-of-Work ↔ Story Map — Technology & Domain Review

**Stage:** units-generation (2.7) · Companion to `unit-of-work.md` / `unit-of-work-dependency.md`.
**Upstream inputs:** `stories.md` (US-1…US-10), `requirements.md`, and Application Design `components.md`,
`component-methods.md`, `services.md`, `component-dependency.md`, `decisions.md` (the story→component
traceability that this story→unit map builds on).

> Maps every user story to its implementing unit(s) and verifies bidirectional coverage: every non-Won't
> story has a unit, and every unit has at least one story. Story→component traceability comes from
> `components.md`; this artifact adds the story→unit layer.

## Story → Unit Assignment

| Story | Title | Implementing unit(s) | Priority |
|---|---|---|---|
| US-1 | Dose-calculation correctness | U1 clinical-dose-review | Must (non-trimmable floor) |
| US-2 | Guardrails & metric-definition correctness | U2 clinical-guardrails-metrics-review | Must |
| US-3 | Data-model completeness | U3 data-model-review | Must |
| US-4 | Security & regulatory posture | U4 security-review | Should |
| US-5 | Test pyramid, coverage & static-analysis debt | U5 tech-debt-review | Should |
| US-6 | Stack currency, boundaries, CI/CD & observability | U6 modernization-review | Could |
| US-7 | Prioritized evidence-linked backlog (docs + issues) | U7 backlog-assembly (docs) + U10 issue-materialization (issues, deferred) | Must |
| US-8 | Quick-wins list | U8 quick-wins | Must |
| US-9 | Phased roadmap | U9 roadmap | Should |
| US-10 | Deferred implementation & out-of-scope areas | (none — Won't Have this run) | Won't |

## Cross-cutting Concerns (stories spanning multiple units)

- **US-7 spans two units** — the backlog exists as **both** `docs/review/` markdown (U7) **and** GitHub
  issues (U10, deferred), per FR-D.1. U7 is the value-bearing half that runs this intent; U10's execution is
  deferred to the end-of-Inception continue decision (`decisions.md` ADR-RVW-005).
- **The evidence-linkage + live-verification concern (NFR-1, US-5 currency guard)** is factored into U0 and
  consumed by every theme unit (U1–U6) — a cross-cutting enabler rather than a per-story unit.
- **The MVR floor (FR-1.4)** is isolated in U1 so a capacity cut drops other units first; U1 carries US-1
  alone precisely so it can survive independently.

## Story Implementation Order Within Units

Each unit is small enough that its story maps 1:1, except:

- **U1** implements the six dose dimensions of US-1 (bolus formula → IOB → ISF/correction → carb-ratio →
  unit handling → rounding/guardrails) — internal order only, not a cross-unit build order.
- **U2** implements US-2's five checks (implausible-dose limit, correction-bolus stacking, TIR, AGP,
  HbA1c/GMI).
- **U7** produces the backlog then the README index (index last, since it references the finished doc set).

Within-unit ordering is descriptive; cross-unit build order is decided in Delivery Planning (2.8) from the
DAG in `unit-of-work-dependency.md`.

## Coverage Verification

- **Every non-Won't story (US-1…US-9) is assigned** to at least one unit. ✓
- **Every unit (U0…U10) has a story or is a declared enabler:** U0 is the cross-cutting enabler for
  NFR-1/US-5; U1–U9 map to US-1…US-9; U10 carries the issues half of US-7. ✓
- **US-10 (Won't) is intentionally unassigned** — no unit implements deferred/out-of-scope work this run. ✓
- No story is orphaned; no non-enabler unit is storyless. Coverage is complete and consistent with
  `stories.md` and `requirements.md`.
