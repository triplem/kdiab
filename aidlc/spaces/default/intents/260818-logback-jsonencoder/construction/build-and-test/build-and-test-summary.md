# Build & Test Summary — logback-jsonencoder

## Outcome: RESOLVED — the finding was actioned; the corrected deliverable shipped CI-green

The build-and-test stage did its job: it caught a false premise before it reached `main`, surfaced the
decision to the human, and the corrected value shipped through normal CI. Closing the stage on resume.

- **Corrected deliverable merged (#1605, PR #1611, CI-green):** native `JsonEncoder` across all 8
  backends + `logback-contrib` removed, **keeping the jackson 2.21.4 CVE force-pin** (verified on disk).
- **Swagger→jackson path removed (#1607, merged).** The last jackson path — `com.auth0:java-jwt` — is
  tracked as **#1606** under epic **#1603** (full jackson removal). **#1556** closed as superseded.
- **Local proof (post-merge tree):** `kdiab-calc :compileKotlin` → EXIT 0.

The section below is the original parked-point disposition, preserved for the audit trail.

---

## Outcome (at park): HALTED at build-and-test — intent superseded by epic #1603

Build & Test ran the #1556-specific acceptance check (AC-1: jackson off the runtimeClasspath) and it
**failed**, revealing that the intent's premise was invalid. See
[`build-test-results.md`](./build-test-results.md) for the full finding.

## The finding (value delivered by this workflow)

`dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` proved jackson is
pulled by **three independent runtime paths**, all present in every service:

- `com.auth0:java-jwt` ← `ktor-server-auth-jwt` (JWT auth, via `kdiab-common`)
- `io.swagger:swagger-parser/codegen` ← `ktor-server-openapi` (Swagger UI)
- `logback-jackson` (logging — the only path #1556 accounted for)

So the encoder swap alone cannot shed jackson, and removing the force-pin would downgrade jackson
2.21.4 → 2.21.3 (re-introducing CVE-2026-54512/54513). The change was **reverted** (never committed,
never pushed) to avoid the security regression.

## Disposition

- **Intent parked** at the build-and-test boundary; it is **superseded** — do not resume it as-is.
- **#1556 closed** as superseded (premise invalid).
- **Epic #1603** created (full jackson removal) with sub-issues #1604 (audit), #1605 (the corrected
  encoder swap — the valid half of #1556), #1606 (replace java-jwt), #1607 (static Swagger),
  #1608 (remove jackson + retire pin), #1609 (ADR). All linked as native sub-issues.
- The code tree is back to main state; only this intent's aidlc records remain (uncommitted).

## What passed / what was not run

- AC-1 (jackson-free classpath): **FAIL** — jackson present via jwt + swagger.
- Full `./gradlew check`: **not completed** — stopped once AC-1 disproved the premise (no point
  verifying a change that must be reverted).
- The valid sub-change (encoder swap + drop logback-contrib, keeping the jackson pin) will be
  delivered and verified under **#1605**, not here.
