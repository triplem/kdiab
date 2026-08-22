# User Stories Assessment — #1606 (Jackson-free JWT)

INVEST assessment, dependency mapping, and MVP/critical-path analysis for the seven stories in
`stories.md`. Traces to `../requirements-analysis/requirements.md`,
`../../../codekb/kdiab-bkp/business-overview.md`, `../../../codekb/kdiab-bkp/component-inventory.md`;
governed by `../practices-discovery/team-practices.md`.

## INVEST Assessment

| Story | Independent | Negotiable | Valuable | Estimable | Small | Testable |
|---|---|---|---|---|---|---|
| US-1 tests | ✅ (vs current code) | ✅ | ✅ (safety net) | ✅ M | ✅ | ✅ |
| US-2 dep swap | ⚠️ (needs US-3/4 to compile) | ✅ | ✅ | ✅ S | ✅ | ✅ (resolution) |
| US-3 Nimbus verifier | ✅ (via US-1) | ✅ | ✅ (core) | ✅ L | ⚠️ largest | ✅ |
| US-4 test minting | ✅ (per-service build) | ✅ | ✅ | ✅ M | ✅ | ✅ |
| US-5 realm/config | ✅ | ✅ | ✅ (conditional) | ✅ S | ✅ | ✅ |
| US-6 sweep+pin | ✅ (dep proof) | ⚠️ (gated on sweep) | ✅ | ✅ M | ✅ | ✅ |
| US-7 ADR/docs | ✅ | ✅ | ✅ | ✅ S | ✅ | ✅ |

**Note on US-2 independence:** the dependency swap can't ship in isolation (code won't compile without
US-3/US-4). This is the same tight coupling flagged in the intent-backlog — all stories land in one PR,
so per-story independence is about *testability/sequencing*, not separate delivery.

## Dependency Graph (build order)

```
US-1 (tests, risk-first) ──┐
US-2 (dep swap) ───────────┼──> US-3 (Nimbus verifier) ──> US-6 (sweep + pin removal)
                            │        │                          ^
US-2 ──> US-4 (test minting)┘        ├──> US-5 (realm/config) ──> US-7 (ADR/docs)
                                     └──> US-4 also gated by US-2
```
<!-- Text fallback: US-1 and US-2 have no deps. US-3 depends on US-1+US-2. US-4 depends on US-2. US-5 and US-7 depend on US-3 (US-7 also on US-5). US-6 depends on US-3+US-4. Critical path: US-2 -> US-3 -> US-6. -->

## Critical Path & Sequencing

- **Risk-first:** US-1 first (pins current behaviour), even though it has no code dependency — it is the
  regression net for US-3.
- **Critical path:** US-2 → US-3 → US-6 (dep swap → verifier → sweep/pin). US-3 (L) is the long pole.
- **Parallelizable:** US-1 and US-2 can start together; US-4 can proceed once US-2 lands; US-5/US-7 trail US-3.
- All stories ship in **one atomic PR** (`Closes #1606`, merge-commit) per team practice — the graph is
  internal build order, not separate PRs.

## Coverage Check (every FR has a story)

| FR | Story | | FR | Story |
|---|---|---|---|---|
| FR-1 | US-2, US-6 | | FR-6 | US-1, US-3 |
| FR-2 | US-3 | | FR-7 | US-5 |
| FR-3 | US-1, US-3 | | FR-8 | US-6 |
| FR-4 | US-1, US-3 | | FR-9 | US-7 |
| FR-5 | US-1, US-3 | | FR-10 | US-4 |

No orphan requirement; no orphan story. **NFR-1 (security review) and NFR-6 (whole-platform CI-green +
Kover ≥80% + atomic PR) are pinned by the dedicated release-gate story US-8** (AC-8a/8b/8c) — added
after the §12a review flagged that these Must obligations had no checkable exit AC. NFR-4 (supply-chain)
verified across US-3/US-6; NFR-2/NFR-5 via US-1/US-3; NFR-3 (DRY) cross-cutting (single shared file).

**Module count reconciled (post-review):** the platform is **8 backend services + `kdiab-common` = 9
Gradle modules** (+ kdiab-ui for CI). The change lives in `kdiab-common`; the US-6 sweep and the US-8
coverage floor include it — do not gate one module short.
