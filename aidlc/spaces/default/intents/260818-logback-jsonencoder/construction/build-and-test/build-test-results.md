# Build & Test Results — logback-jsonencoder

## Verdict: ✅ RESOLVED — finding actioned; corrected deliverable shipped CI-green

> **Resolution (2026-08-18, on resume).** The FAIL below is the historical finding against the
> **original over-broad #1556 scope** and is preserved verbatim for the audit trail. Since this stage
> was parked, the human decision it surfaced has been executed and the value has shipped:
>
> - **#1605** (merged, PR #1611, CI-green) — the corrected encoder-swap deliverable: all 8 backends
>   swapped to `ch.qos.logback.classic.encoder.JsonEncoder`; `logback-contrib`
>   (`logback-json-classic` + `logback-jackson`) removed from the catalog; **the jackson force-pin at
>   the patched 2.21.4 was correctly RETAINED** (jackson stays load-bearing via jwt + swagger). This is
>   exactly the "Corrected scope" recommended below. Verified on disk now: `JsonEncoder` present ×8,
>   no `logback-contrib` in `gradle/libs.versions.toml`, `jackson = "2.21.4"` pin + CVE comment intact.
> - **#1607** (merged) — dropped the unused `ktor-server-openapi`, removing the swagger-codegen →
>   jackson runtime path (one of the two remaining jackson consumers this stage identified).
> - **#1603** (open epic) — full jackson removal; **#1606** (open) — replace `com.auth0:java-jwt`
>   with a jackson-free JWT verification, the last remaining runtime jackson path.
> - **#1556** closed as superseded.
>
> Local build proof on the current (post-merge) tree: `kdiab-calc :compileKotlin` → **EXIT 0**. The
> corrected deliverable itself passed the full GitHub Actions gate on merge of #1611/#1607 (tests,
> Kover, Detekt, SonarCloud, CodeQL, Trivy) — the authoritative build-and-test signal for a merged
> change. Nothing over-broad remains in the working tree (the 11-file change was reverted, never
> committed).

---

## Historical finding (original #1556 scope): ❌ FAIL — AC-1/AC-4/AC-6 not met; #1556's premise is invalid

The build-and-test verification revealed that a core assumption of issue #1556 is **factually
wrong**, and that the change as implemented would introduce a **security regression**.

## Finding — jackson is load-bearing, not "solely logback-jackson"

`dependencyInsight --dependency jackson-databind --configuration runtimeClasspath` (kdiab-analyze,
representative of all 8) shows `jackson-databind:2.21.3` pulled by **two runtime paths unrelated to
logging**:

1. **`com.auth0:java-jwt:4.5.2` → `io.ktor:ktor-server-auth-jwt-jvm:3.5.0`** — JWT authentication,
   pulled by `kdiab-common`, so present in **every** service.
2. **`io.swagger:swagger-core` / `swagger-parser` / `swagger-codegen` → `io.ktor:ktor-server-openapi-jvm:3.5.0`**
   — the Swagger UI / OpenAPI serving, present in **every** service.

#1556 claimed "jackson is on the classpath solely because of logback-jackson." That is false —
removing `logback-jackson` leaves jackson fully present via JWT auth + Swagger.

## Consequence — the change as-implemented is a security regression

The change removed the `jackson` force-pin (`kdiab.kotlin-base.gradle.kts` constrained jackson to the
**patched 2.21.4** for CVE-2026-54512/54513). With the pin gone, jackson resolves by conflict
resolution to **2.21.3 — the vulnerable version**. So the change would:

- ❌ **AC-1** — jackson still on all 8 runtimeClasspaths (via jwt + swagger).
- ❌ **AC-4** — the jackson constraint was removed, but it is still needed.
- ❌ **AC-6** — SBOM still lists jackson; Trivy would re-flag CVE-2026-54512/54513 (HIGH).
- ⚠️ **Security regression** — jackson downgraded 2.21.4 → 2.21.3 (unpatched).

## What IS still valid

- ✅ The 8 × `logback.xml` swap to native `JsonEncoder` — correct; removes the logback-contrib
  formatter from the logging path.
- ✅ Removing the `logback-contrib` libs (`logback-json-classic`, `logback-jackson`) + version from
  the catalog — correct; those were purely for log formatting.
- ✅ `logging.md` reconciliation — correct.

## Corrected scope (if we proceed — reduced value)

Keep the encoder swap + logback-contrib removal, but **KEEP** the `jackson` version, the
`jackson-core`/`jackson-databind` lib entries, and the build-logic force-pin constraint (jackson stays
on the classpath via jwt + swagger and must stay patched). Net result: drops the two logback-contrib
0.1.5 libs and modernizes the log encoder, but **does NOT shed jackson and does NOT retire the CVE
pin** — the headline goal of #1556 is not achievable without also replacing `com.auth0:java-jwt` and
the `ktor-server-openapi` Swagger serving (a much larger, auth-touching change).

## Status: HALTED — awaiting human decision on scope (see gate)
