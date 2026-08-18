# Requirements Analysis — logback-jsonencoder (refactor)

> **Intent:** Replace `logback-contrib` `JacksonJsonFormatter` + the transitive jackson stack
> with Logback's built-in `ch.qos.logback.classic.encoder.JsonEncoder` across all 8 backend
> services, and retire the jackson CVE force-pins. **Source:** GitHub issue #1556.
> **Scope:** refactor (Minimal depth). **Type:** brownfield.

## 1. Problem statement (traced to #1556)

All 8 runnable backends emit structured JSON logs via a `logback-contrib`
`JsonLayout` + `JacksonJsonFormatter` (introduced in #34). This drags three dependencies onto
every backend's runtime classpath purely to format log lines:

- `ch.qos.logback.contrib:logback-json-classic` (0.1.5)
- `ch.qos.logback.contrib:logback-jackson` (0.1.5)
- the transitive **jackson** stack (`jackson-core`, `jackson-databind`)

The application's own JSON is handled by **kotlinx.serialization** (Ktor `ContentNegotiation`);
jackson is on the classpath *only* because of `logback-jackson`. It is a recurring security
liability: `build-logic/src/main/kotlin/kdiab.kotlin-base.gradle.kts` force-pins
`jackson-core`/`jackson-databind` to 2.21.4 to clear HIGH Trivy findings (CVE-2026-54512 /
CVE-2026-54513). Every new jackson CVE forces another pin bump.

Logback 1.5.32 (in use) ships a first-party `JsonEncoder` that needs no third-party formatter and
no jackson.

## 2. Functional requirements

| ID | Requirement | Trace |
|---|---|---|
| FR-1 | All 8 backends (analyze, calc, carbs, measures, nightscout, profiles, treatments, users) emit one-line JSON to stdout via `ch.qos.logback.classic.encoder.JsonEncoder`. | #1556 Proposal |
| FR-2 | No `logback-contrib` and no `jackson` artifact appears on any backend's `runtimeClasspath`. | #1556 AC-1 |
| FR-3 | The `X-Correlation-ID` → MDC `Correlation-ID` trace field continues to surface in the emitted JSON (under the encoder's `mdc` object). | #1556 AC-3 |
| FR-4 | The jackson version constraints are removed from `kdiab.kotlin-base.gradle.kts`; the neighbouring **`handlebars` constraint is kept** (unrelated). | #1556 AC-4 |
| FR-5 | `gradle/libs.versions.toml` drops the `logback-json-classic`, `logback-jackson`, `jackson-core`, `jackson-databind` entries and their version refs; the `logging` bundle becomes `["kotlin-logging", "logback-classic"]`. | #1556 Scope |
| FR-6 | `.claude/rules/logging.md` and the Loki log-aggregation pipeline (#1023) are reconciled with the new field names / timestamp format (see §4 decision). | #1556 AC-5 |
| FR-7 | The CycloneDX SBOM no longer lists jackson / logback-contrib; Trivy has no jackson finding left to pin. | #1556 AC-6 |

## 3. Non-functional requirements & constraints

| ID | Requirement |
|---|---|
| NFR-1 | Log output stays valid one-line JSON to stdout (12-factor); no change to the appender name (`STDOUT`) or the `${LOG_LEVEL:-INFO}` root / Ktor `Application` logger. |
| NFR-2 | `./gradlew check` (tests + Detekt + Kover ≥ 80%) stays green for all backends; all GitHub Actions (backend CI, CodeQL, Trivy, SonarCloud, SBOM) green before merge. (team-practices) |
| NFR-3 | One feature branch `chore/1556-logback-jsonencoder`, Conventional Commits, **merge-commit not squash**, `Closes #1556`. (team-practices) |
| NFR-4 | No secrets or PII in logs; correlation-ID tracing preserved (logging.md). |
| C-1 | The native `JsonEncoder` schema is **fixed**: timestamp is epoch-millis (not ISO-8601, not configurable to it); keys are `formattedMessage` / `loggerName` / `threadName` (not renamable). Fields are toggleable but not renamable. |
| C-2 | Fallback if exact schema is a hard requirement: `net.logstash.logback:logstash-logback-encoder` — but it is **jackson-based**, so it would NOT shed jackson (defeats the primary goal). |

## 4. Key requirements decision (blocks design) — log-schema acceptance

The primary goal (shed jackson) is achievable **only** if the native `JsonEncoder`'s fixed schema
is acceptable. This conflicts with `.claude/rules/logging.md`, which currently mandates an
**ISO-8601 UTC `timestamp`** and the field names `level`, `service`, `traceId`, `spanId`,
`message`, `env`. The native encoder instead emits:

| Concern | `JsonLayout` (today) | native `JsonEncoder` |
|---|---|---|
| timestamp | ISO-8601 `…SSSX` | **epoch millis** (fixed) |
| message key | `message` | `formattedMessage` |
| logger key | `logger` | `loggerName` |
| thread key | `thread` | `threadName` |
| MDC (incl. `Correlation-ID`) | `mdc` object | `mdc` object (unchanged) |

**Decision required (see requirements-analysis-questions.md Q1):** accept the native schema and
update `logging.md` + the Loki pipeline (#1023) to match — OR treat ISO-8601 + exact field names
as a hard requirement and fall back to the jackson-based logstash encoder (keeping jackson).

**RESOLVED (2026-08-18): Option A — accept the native `JsonEncoder` schema.** The native encoder is
adopted with its epoch-millis timestamp and `formattedMessage`/`loggerName`/`threadName` keys;
`.claude/rules/logging.md` and the Loki pipeline (#1023) are updated to match. This is the only path
that fully sheds jackson and retires the CVE force-pins, so FR-2/FR-4/FR-5/FR-7 hold. FR-6 is
therefore an in-scope **doc + pipeline update** (not a blocker). Correlation-ID (FR-3, AC-3) is
verified by a **unit-level assertion** on `mdc.Correlation-ID`, with full runtime confirmation
deferred to CI/e2e (Q2 = C).

## 5. Acceptance criteria (testable, from #1556)

- [ ] AC-1 — `./gradlew :kdiab-measures:dependencies --configuration runtimeClasspath | grep -iE 'jackson|logback-contrib'` is **empty** for all 8 services.
- [ ] AC-2 — all 8 services log valid one-line JSON via `JsonEncoder` (verified by running each and inspecting stdout).
- [ ] AC-3 — `Correlation-ID` (from `X-Correlation-ID`) present in the emitted JSON `mdc`.
- [ ] AC-4 — jackson CVE constraints removed from `kdiab.kotlin-base.gradle.kts`; `handlebars` kept.
- [ ] AC-5 — `logging.md` and Loki (#1023) reconciled/updated per the §4 decision.
- [ ] AC-6 — SBOM has no jackson/logback-contrib; Trivy has no jackson finding to pin.
- [ ] AC-7 — `./gradlew check` green for every backend.

## 6. Traceability

Every requirement traces to issue #1556 (Context / Proposal / Scope / Acceptance criteria / Quality
gates). No requirement is introduced without an origin in #1556 or the team practice files
(org.md / team.md / project.md). The codekb (`dependencies.md`, `technology-stack.md`,
`architecture.md`) confirms the current logback-contrib + jackson-pin arrangement across the 9
modules and is authoritative as of the 2026-08-18 currency check (commit a3acc571).

## 7. Out of scope

- kdiab-ui logging (frontend uses Pino; unaffected).
- Any change to what is logged (levels, messages, MDC keys the app sets) beyond the encoder swap.
- The TypeScript-7 / Loki-infra work (separate issues).
