# Business Rules — Invariants the change must preserve

> For this infra refactor, "business rules" are the **invariants** the encoder swap must not break.
> Each maps to a `requirements.md` acceptance criterion and is checked at `build-and-test`.

## Invariants (must hold after the change)

| # | Invariant | Trace | Check |
|---|---|---|---|
| INV-1 | Every backend still emits **valid one-line JSON** to stdout (no pretty-print, one object per line). | FR-1, AC-2 | boot service, inspect stdout is parseable single-line JSON |
| INV-2 | **`Correlation-ID`** (from `X-Correlation-ID` via the `CallId`/MDC path) appears in the JSON `mdc` object. | FR-3, AC-3 | unit assertion on `mdc.Correlation-ID` (Q2 = C); full check deferred to CI/e2e |
| INV-3 | **No `jackson` and no `logback-contrib`** artifact on any backend `runtimeClasspath`. | FR-2, AC-1 | `./gradlew :kdiab-<svc>:dependencies --configuration runtimeClasspath \| grep -iE 'jackson\|logback-contrib'` is empty for all 8 |
| INV-4 | The **`handlebars` constraint stays**; only the jackson constraints are removed. | FR-4, AC-4 | grep `kdiab.kotlin-base.gradle.kts` still contains the handlebars `constraints` line |
| INV-5 | The `STDOUT` appender name, `${LOG_LEVEL:-INFO}` root level, and `io.ktor.server.application.Application` logger config are **unchanged**. | NFR-1 | diff each `logback.xml`: only the encoder block changes |
| INV-6 | `./gradlew check` (tests + Detekt + Kover ≥ 80%) stays **green** for all backends. | NFR-2, AC-7 | CI |
| INV-7 | SBOM (CycloneDX) lists **no jackson / logback-contrib**; Trivy has no jackson finding to pin. | FR-7, AC-6 | CI SBOM + Trivy |
| INV-8 | No secrets / PII introduced into logs; the set of MDC keys the app writes is unchanged. | NFR-4 | review the field set — only the serializer changed, not what is logged |

## Decision rules baked into the design

- **Schema conflict → accept native (Q1 = A).** Where `logging.md`'s mandated ISO-8601 + names
  conflict with the encoder's fixed schema, the encoder wins and `logging.md` is updated. Rationale:
  removing jackson is the goal; the logstash fallback would keep jackson.
- **All-or-nothing landing.** The libs-catalog/build-logic edits and the 8 XML edits must merge
  together — a partial state (libs dropped but XML still on `JsonLayout`) breaks service startup.
