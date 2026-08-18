# Functional Design — Change Model (logback-jsonencoder)

> **Nature of this stage for this intent.** #1556 is an infrastructure/logging refactor: it introduces
> **no new business logic, no domain entities, and no frontend components** (the functional-design
> stage's own condition explicitly says "skip if simple logic changes with no new business logic").
> So this document repurposes `business-logic-model` as the **concrete technical change blueprint**
> that `code-generation` will implement; `domain-entities.md` and `frontend-components.md` are marked
> N/A. This design realizes `requirements.md` FR-1…FR-7 under the resolved Q1 = Option A (accept the
> native `JsonEncoder` schema).

## Current state (verified at a3acc571)

- **8 backend `logback.xml`** (analyze, calc, carbs, measures, nightscout, profiles, treatments,
  users). 6 are byte-identical; **nightscout + users differ only by two XML comment lines** — the
  encoder block is identical in all 8. Current encoder block:

  ```xml
  <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
      <layout class="ch.qos.logback.contrib.json.classic.JsonLayout">
          <jsonFormatter class="ch.qos.logback.contrib.jackson.JacksonJsonFormatter">
              <prettyPrint>false</prettyPrint>
          </jsonFormatter>
          <timestampFormat>yyyy-MM-dd'T'HH:mm:ss.SSSX</timestampFormat>
          <appendLineSeparator>true</appendLineSeparator>
      </layout>
  </encoder>
  ```

- **`gradle/libs.versions.toml`** — `logback-contrib = "0.1.5"` (L9), `jackson = "2.21.4"` (L32,
  with CVE comment L31), libs `logback-json-classic` (L61), `logback-jackson` (L62), `jackson-core`
  (L63), `jackson-databind` (L64); `logging` bundle (L112) = `["kotlin-logging", "logback-classic",
  "logback-json-classic", "logback-jackson"]`.
- **`build-logic/src/main/kotlin/kdiab.kotlin-base.gradle.kts`** — a `constraints { }` block (L25–28)
  pinning `jackson-core` + `jackson-databind` (L26–27, CVE-2026-54512/54513) and `handlebars` (L28,
  CVE-2026-55760).

## Target state — the change, file by file

### Change 1 — the encoder block (× 8 `logback.xml`)

Replace the whole `<encoder …LayoutWrappingEncoder>…</encoder>` block with the native encoder,
preserving the `STDOUT` appender name, the `${LOG_LEVEL:-INFO}` root, and the Ktor `Application`
logger. Preserve each file's existing comments (keep nightscout/users variant as-is except the
encoder):

```xml
<encoder class="ch.qos.logback.classic.encoder.JsonEncoder"/>
```

The native `JsonEncoder` defaults already emit the fields we need (`timestamp` epoch-millis,
`formattedMessage`, `loggerName`, `level`, `threadName`, `context`, `mdc`). No `<with…>` toggles are
required for parity with today's consumed fields; `mdc` (carrying `Correlation-ID`) is on by default.

### Change 2 — `gradle/libs.versions.toml`

- Remove `[versions]` `logback-contrib` (L9) and `jackson` + its CVE comment (L31–32).
- Remove `[libraries]` `logback-json-classic`, `logback-jackson`, `jackson-core`, `jackson-databind`
  (L61–64).
- `logging` bundle (L112) → `["kotlin-logging", "logback-classic"]`.
- Keep `logback = "1.5.32"` and `logback-classic` (the native `JsonEncoder` ships in logback-classic).

### Change 3 — `build-logic/.../kdiab.kotlin-base.gradle.kts`

- Remove the two jackson constraint lines (L26–27) and the jackson CVE comment (L21–22).
- **Keep** the `constraints { }` block and the `handlebars` constraint (L28) — unrelated
  (CVE-2026-55760). Handlebars comes from the OpenAPI generator toolchain, not jackson.

### Change 4 — `.claude/rules/logging.md` (doc reconciliation, FR-6)

Reconcile the mandated-fields table with the native encoder's fixed schema: `timestamp` is
epoch-millis (document that the platform's JVM services emit epoch-millis, ISO-8601 remains the
target for non-JVM/Pino services), and record the field-name mapping `message→formattedMessage`,
`logger→loggerName`, `thread→threadName`. Correlation-ID stays under `mdc`.

### Change 5 — Loki pipeline (#1023) (FR-6)

Update the Loki parser/labels for the JVM backends to the new keys + epoch-millis timestamp. If the
Loki pipeline config is not in this repo, this becomes a documented follow-up note on #1023 rather
than an edit (confirm during code-generation).

## Data flow (unchanged)

`slf4j/kotlin-logging → Logback root(STDOUT) → JsonEncoder → one-line JSON on stdout`. The
`X-Correlation-ID` → `CallId` plugin → MDC `Correlation-ID` path is untouched; only the terminal
serialization step changes. No code (`.kt`) changes — this is configuration + build metadata only.

## Blast radius & sequencing

11 files: 8 × `logback.xml` + `libs.versions.toml` + `kdiab.kotlin-base.gradle.kts` + `logging.md`
(+ possibly Loki config). Single unit, single Bolt — no ordering constraints between files; the
version-catalog + build-logic edits and the 8 XML edits must land together (a service whose XML still
references `JsonLayout` after the libs are dropped would fail to start). Verified together by
`./gradlew check` + the runtime-classpath grep (AC-1).
