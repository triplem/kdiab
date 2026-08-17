# Application Design — Stage Diary

Observation log for the Application Design stage. Maintained by the orchestrator, never hand-edited.
Four standard headings; ISO 8601 timestamps.

## Interpretations

- 2026-08-16T14:10:57Z — Treated Application Design as designing the *review deliverable system*, not a software system; this intent is recommendations-only (parks at end of Inception, RA-Q3=A). The "components" are the logical review artefacts (finding record, theme workstreams, backlog, quick-wins, roadmap, GitHub-issue sync); "services" are the two production pipelines (doc generation, issue materialization). This is the only coherent reading of a design stage on an assessment intent — the deliverable architecture IS the thing being designed.

- 2026-08-16T14:20:00Z — Split "clinical" into two theme docs (clinical-safety + data-model) to match Q1's 5-doc choice; requirements group both under Theme 1 (FR-1.x). Kept them separate because data-model completeness (FR-1.5) is a distinct assessment lens from dose/guardrail correctness (FR-1.1/1.2) and cuts independently.

## Deviations

- 2026-08-16T14:20:30Z — Numbered the design ADRs `ADR-RVW-NNN`, deviating from the project's `ADR-NNN` (platform) / `ADR-{SVC}-NNN` (service) convention in github-issue-management.md. Rationale: these are design-record ADRs for the *review deliverable system*, not kdiab platform/service ADRs; a distinct prefix prevents collision with real kdiab ADRs the review may later recommend.

## Tradeoffs

- 2026-08-16T14:21:00Z — Made C5.bandOf() the single authority for both the per-finding roadmap-phase tag (Q2=B) and the roadmap grouping (Q7=A), rather than letting each doc assign phase independently. Removes a real drift risk (backlog tag vs roadmap band disagreeing) at the cost of a hard C3→C5 dependency for stamping.
- 2026-08-16T14:21:20Z — Chose Q8=B (path+symbol, no line, no commit pin) over path+line pinned to d6c8866b. Trade precise line-jumps for durability: symbol links survive refactors and a pinned commit would mask that main has since resolved items (the exact US-5 currency risk).

## Open questions

- 2026-08-16T14:21:40Z — Whether C6 GitHub issue materialization should be executed in a follow-up intent immediately after the end-of-Inception park, or held until the maintainer explicitly re-confirms continue (ties to OQ-1 park/continue re-confirmation). Designed as deferred either way.
- 2026-08-16T14:34:00Z — Framework tooling note (not a stage learning): `runtime-graph.json` records this stage's `memory_path` as `aidlc/spaces/default/intents/inception/application-design/memory.md`, MISSING the intent record-dir segment `260815-tech-domain-review`. The `run-stage` directive's `memory_path` is correct (includes the record dir). Consequence: `aidlc-learnings.ts surface` reads a non-existent path and returns 0 candidates even when the diary has entries. Worked around by building the persist selections by hand (persist writes to the fixed project.md path, unaffected). Worth a framework bug report.
