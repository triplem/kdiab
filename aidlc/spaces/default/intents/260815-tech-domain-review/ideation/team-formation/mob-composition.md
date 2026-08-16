# Mob / Working Composition — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Date:** 2026-08-15

> No human mob exists (solo). "Composition" here = which **AI-DLC agent personas** lead which parts of the
> review as it moves through the remaining stages, plus the project's own domain-review skills.

## Virtual composition for the review

| Review theme (from `intent-backlog.md`) | Leading persona(s) | Project skills to invoke |
|---|---|---|
| Clinical safety (`kdiab-calc`, analytics) | developer (RE) → architect (design) | `/doctor-t1d-review`, `/patient-t1d-review` |
| Security & compliance | devsecops + compliance | — |
| Code health (tests, Detekt, duplication) | quality + developer | `/kotlin-patterns` |
| Modernization / architecture | architect (+ aws-platform = **N/A**, self-hosted) | `/openapi-patterns`, `/react-patterns` |

## Working mode

- **Single-threaded** — one person, one context; no parallel workstreams, no mob rotation.
- The AI-DLC conductor sequences the review stages; the maintainer approves at each gate.
- **Bus-factor mitigation:** every finding is written to stand alone (evidence-linked, Q8) so the review
  survives independent of the person who ran it.

## Verdict

Team Formation is **N/A** for a solo maintainer. Recorded for completeness; no team actions required. The
one actionable staffing note lives in `skill-matrix.md`: the **clinical-domain gap** warrants external
validation before `kdiab-calc` is treated as more than advisory.
