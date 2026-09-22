# Reverse Engineering — Freshness Marker

## Analysis Metadata

| Field | Value |
|---|---|
| Full scan performed | 2026-08-16 (commit d6c8866b) — enterprise scope, whole monorepo |
| Last freshness refresh | 2026-08-30 (commit a14944e4) — bugfix intent agp-tooltip-drift |
| Repository | kdiab-bkp (single Git repo) |
| Branch | main |
| Project type | Brownfield |
| Refresh intent | "fix the agp chart tooltip drift" (slug: agp-tooltip-drift) |
| Refresh performer | AI-DLC reverse-engineering stage — freshness refresh (existing codekb reused) |

## Refresh Note (2026-08-22, intent #1617)

This pass is a **freshness refresh**, not a re-scan. The 8 codekb content artifacts
(business-overview, architecture, code-structure, api-documentation, component-inventory,
technology-stack, dependencies, code-quality-assessment) from the 2026-08-16 full-monorepo scan are
**reused as-is** — the monorepo module/service structure is unchanged and remains accurate. Rationale:
Minimal-depth refactor scope; the change under this intent is a ~16-line CI-workflow name fix
(`.github/workflows/release.yml`), so a full 9-module re-scan is disproportionate.

### Known deltas since the 2026-08-16 full scan (out of scope for #1617)

- **#1606 jackson-free JWT** merged 2026-08-21 (commit 209cd817): `kdiab-common` `Security.kt` now uses
  a custom Nimbus (`com.nimbusds:nimbus-jose-jwt`) `AuthenticationProvider`; `com.auth0:java-jwt`,
  `jwks-rsa`, and jackson removed from the runtime classpath; jackson force-pins removed (handlebars
  pin retained). ⇒ `dependencies.md` and `technology-stack.md` are slightly stale on the JWT-library
  detail only. Not regenerated here — irrelevant to the CI release-workflow fix. Refresh those on the
  next in-scope reverse-engineering pass.

## Refresh Note (2026-08-25, intent #1563)

Second **freshness refresh**, not a re-scan. Only source delta since the 2026-08-22 refresh
(`209cd817..88428807`) is `gradle/libs.versions.toml` (5 lines — dead jackson catalog entries dropped,
#1608); everything else is `aidlc/` workflow records. Module/service structure unchanged.

Applied this pass (jackson-removal drift the #1617 note flagged as deferred is now cleared):

- **dependencies.md** — dropped the Jackson force-pin bullet and the `logback-contrib JSON` logging
  row; noted Jackson removed from the runtime classpath entirely (epic #1603: #1605 built-in
  `JsonEncoder`, #1606 Nimbus JWT, #1607 static Swagger, #1606/#1608 force-pin + catalog retired).
- **technology-stack.md** — removed the Jackson row from the security-pinned-transitives table
  (Handlebars pin retained).
- **code-structure.md** — `kdiab.kotlin-base` pin note now reads "(Handlebars; Jackson pin retired #1608)".

Directly re-verified for #1563: `kdiab-calc` `DoseCalculation.kt#DoseRequest` still carries
`activeIob: Double = 0.0` (line 10), consumed in `DoseCalculationService.calculateDose` at line 66
(`maxOf(0.0, rawCorrection - request.activeIob)`) and line 79 (`iobCoversFullCorrection`). Finding
FIND-CLIN-001 confirmed valid against live source.

## Refresh Note (2026-08-30, intent agp-tooltip-drift)

Third **freshness refresh**, not a re-scan. Source delta since the 2026-08-25 refresh
(`88428807..a14944e4`) is entirely the merged kdiab-calc iob-required work (#1563 / PR #1644):
`kdiab-calc` DoseCalculation/Service/Mapper + tests, its `api/openapi.yaml`, and the paired
`kdiab-ui` `calcApi.ts` / `DoseCalculator.tsx` / i18n keys. **Zero change to the monorepo
module/service structure** — the 8 codekb content artifacts remain accurate and are reused as-is.
No content artifact regenerated this pass.

Directly re-verified for the AGP tooltip-drift bug: `kdiab-ui/src/features/analytics/AgpChart.tsx`
renders a Recharts `<AreaChart data={chartData}>` with `<XAxis dataKey="minuteOfDay">` that declares
**no `type`** (defaults to `type="category"`) while supplying numeric `ticks={0,180,…,1440}` and a
`tickFormatter`. Three `<Area>` series (`p10_p90` range, `p25_p75` range, `median` line) plus a
`<Tooltip>` with `labelFormatter`/`formatter`. The category-axis-with-numeric-ticks configuration is
the prime suspect for tooltip index/position drift; the report-print page
`features/report/AgpChartPage.tsx` reuses the same component. Full diagnosis is deferred to
requirements-analysis (2.3) / code-generation (3.5) — this pass only establishes the baseline.

## Scope of the codekb (from the 2026-08-16 full scan)

Covered the **entire monorepo**: 9 backend Gradle modules (kdiab-common shared library + 8 runnable
Ktor services), the React SPA (kdiab-ui), Liquibase migrations, Keycloak config, and the
`.github/workflows/` CI/CD pipeline set. Directly re-verified for #1617: the CI→release artifact flow
(`backend-ci-reusable.yml` uploads `kdiab-<service>-backend-{image,bom}`; `release.yml` downloads the
un-prefixed `<service>-backend-{image,bom}` — the bug).
