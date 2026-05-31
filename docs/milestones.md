# Milestones

## M1 — Nightscout API v3 Parity

**Goal:** Implement the full Nightscout v3 collection API as a translation layer over kdiab upstream services, retaining v1 for backward compatibility.

**Epics:**
- [#288 Epic: Adopt Nightscout API v3 in kdiab-nightscout](https://github.com/triplem/kdiab/issues/288)

**Status:** Closed — all stories merged, epic closed.

**Rationale:** Modern CGM clients (AAPS 3.x, xDrip+, Juggluco) increasingly require v3 API. Unblocks real-device integration.

---

## M2 — UI Quality & Rendering Fixes

**Goal:** Fix UX and rendering bugs discovered during manual acceptance testing. Brings the UI to a level where all tabs are functionally usable and chart data is correctly visualised.

**Epics:**
- [#968 Epic: UI bug fixes from manual testing round — time format, basal chart, CGM dashboard](https://github.com/triplem/kdiab/issues/968)

**Status:** Closed — all stories merged.

**Dependencies:** None.

---

## M3 — E2E & Regression Test Coverage

**Goal:** Replace the two trivial smoke tests with a full Playwright E2E suite covering every major user flow, so that regressions are caught automatically before merge.

**Epics:**
- [#1005 Epic: E2E & Regression Test Coverage for kdiab-ui](https://github.com/triplem/kdiab/issues/1005)

**Status:** Closed — all stories merged, epic closed.

**Dependencies:** M2 complete (UI must be usable before E2E tests are meaningful).

| Story | Title | Outcome |
|---|---|---|
| #1006 | E2E: auth flow (OIDC login → dashboard) | Merged |
| #1007 | E2E: dashboard golden path (window picker, hero tile, trend chart) | Merged |
| #1008 | E2E: treatment CRUD (add BOLUS, verify, edit, archive) | Merged |
| #1009 | E2E: measure CRUD (add BGM, verify) | Merged |
| #1010 | E2E: analytics view (HbA1c, TIR, AGP, basal chart) | Merged |
| #1011 | E2E: profile management (view active profile) | Merged |
| #1012 | E2E: dose calculator flow | Merged |
| #1013 | E2E: user settings (glucose unit switch) | Merged |
| #1014 | docs: manual test plan | Merged |
| #1015 | ci: Playwright as required CI status check | Merged |
| #1028 | fix: replace waitForTimeout with deterministic waits | Merged |

---

## M4 — UI Bug Fixes Round 3

**Goal:** Fix the next round of UX and rendering bugs found during manual testing.

**Epics:**
- [#1016 Epic: UI Bug Fixes — Manual Testing Round 3](https://github.com/triplem/kdiab/issues/1016)

**Status:** Open (in-progress).

**Dependencies:** M3 complete.

---

## M5 — UX & Platform Enhancements

**Goal:** Deliver quality-of-life and platform enhancements: collaborative profile review with diff view, patient notifications, rate limiting, and analytics export.

**Epics:**
- [#1038 Epic: UX & Platform Enhancements](https://github.com/triplem/kdiab/issues/1038)

**Status:** Closed — all stories merged.

**Dependencies:** M4 complete.

| Story | Title |
|---|---|
| #17 | Add patient notification trigger when doctor creates a PROPOSED profile |
| #19 | Add side-by-side diff view when doctor proposes a new profile |
| #24 | Add rate limiting to all Ktor backend services |
| #30 | Add export/print functionality for BFF analytics view |

---

## M6 — Build Infrastructure Modernisation

**Goal:** Eliminate build script duplication across all 9 services via `buildSrc` convention plugins, introduce version catalog bundles, and establish an API-first client distribution model where every service publishes a typed Kotlin client JAR that can be consumed by other services without hand-rolled code generation.

**Epics:**
- [#1365 Epic: Gradle Build Infrastructure — buildSrc convention plugins](https://github.com/triplem/kdiab/issues/1365)
- [#1366 Epic: API Client Distribution — generated Kotlin client JARs per service](https://github.com/triplem/kdiab/issues/1366)
- [#1367 Epic: Consumer Service Migration to Generated Clients + Server Codegen Completion](https://github.com/triplem/kdiab/issues/1367)

**Status:** Open (pending-approval).

**Dependencies:** No functional prerequisites. Can start after M5.

**Delivery order:** #1365 → #1366 → #1367 (each epic unblocks the next).
