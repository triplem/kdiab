# Functional Design — Stage Diary

Stage: functional-design (3.1) · Phase: Construction · Intent: 260822-release-workflow-fix (#1617)
Lead: aidlc-architect-agent · Support: aidlc-developer-agent · Reviewer: aidlc-architecture-reviewer-agent

## Interpretations
- 2026-08-22T09:45Z — No compiled unit list (units-generation/2.7 skipped in refactor scope; unit-of-work.md / application-design artifacts in the consumes list are N/A). Running as a SINGLE stage per the conductor persona ("no compiled unit list → single stage"). Chose unit dir name `release-workflow-fix` (= intent slug). Skeleton stance already classified `off` (team.md skips the ceremony for refactor).
- 2026-08-22T09:45Z — The stage's canonical artifacts (business-logic-model, business-rules, domain-entities, frontend-components) are DOMAIN-shaped; #1617 is a CI/CD workflow-YAML change with no business logic, no domain entities, and no frontend. Producing minimal honest artifacts: the "functional design" is the GitHub Actions mechanic (derive short name → 2 upload name: edits), recorded in business-logic-model.md + business-rules.md; domain-entities.md + frontend-components.md are explicit N/A.

## Deviations
- 2026-08-22T09:45Z — Not inventing business/domain/frontend content that doesn't exist for a CI fix. domain-entities.md and frontend-components.md are one-line N/A stubs with rationale (Minimal depth, CI-config change).

## Tradeoffs
- 2026-08-22T09:45Z — Reviewer (aidlc-architecture-reviewer-agent) is known to HANG in this env (project.md learning). Plan: dispatch background, poll before stop, bounded ~2-3 min cap; if stalled, stop to surface findings. It is advisory — will not block the gate indefinitely.

## Resolved
- 2026-08-22T09:55Z — §12a reviewer (aidlc-architecture-reviewer-agent) verdict READY. Notably it did NOT hang this run (~64s clean completion) — contrary to the project.md hang-warning; the bounded-wait plan was ready but unneeded. Key reviewer insight (kept): the release rename step (release.yml:272-289) acts on downloaded file CONTENTS (backend-image.tar/bom.json) from the `path:` fields, not the artifact `name:` — so a name-only change (path: untouched) cannot break it. 3 findings all non-blocking (F1 run-step is the correct GH Actions mechanism; F2 actionlint not installed → CI/local; F3 green-gate precondition already disclosed). No artifact changes required.

## Open questions
- 2026-08-22T09:55Z — None. The mechanic is fully specified (requirements FR-3 + the 16-name table); reviewer confirmed path:-vs-name: safety.
