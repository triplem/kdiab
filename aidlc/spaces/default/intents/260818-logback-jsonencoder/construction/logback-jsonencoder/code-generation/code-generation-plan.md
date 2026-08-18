# Code Generation Plan — logback-jsonencoder

> Implements the functional-design blueprint for #1556. Executed **inline** by the conductor (not a
> dispatched subagent — the change is 11 fully-specified mechanical edits and inline editing is more
> reliable; deviation recorded in memory.md). Code written to the workspace root on feature branch
> `chore/1556-logback-jsonencoder`.

## Preconditions verified before editing

- **No other jackson consumer.** `grep jackson` across `*.kts`/`*.toml` finds only the build-logic
  constraint (now removed) — jackson reaches the classpath solely via `logback-jackson`. AC-1 grep at
  build-and-test is the proof.
- **Loki pipeline unaffected.** The in-repo OTEL collector feeds Loki via **OTLP**
  (`receivers: [otlp] → exporters: [loki]`), with no `filelog` receiver and no JSON field-name
  parser. The encoder rename does not touch Loki labels/parsers → FR-6 Loki half is a doc-note on
  #1023, not an edit.
- **Bundle wiring.** All 8 services get logging deps via `bundle("logging")` in
  `build-logic/.../kdiab.ktor-service.gradle.kts`; updating the catalog bundle propagates to all.

## Edits (11 files)

| # | File(s) | Edit |
|---|---|---|
| 1 | 8 × `kdiab-*/src/main/resources/logback.xml` | Replaced the `LayoutWrappingEncoder`+`JsonLayout`+`JacksonJsonFormatter` block with `<encoder class="ch.qos.logback.classic.encoder.JsonEncoder"/>`. Preserved `STDOUT` appender, `${LOG_LEVEL:-INFO}` root, Ktor `Application` logger, and each file's comments. |
| 2 | `gradle/libs.versions.toml` | Removed `[versions]` `logback-contrib`, `jackson` (+CVE comment); removed `[libraries]` `logback-json-classic`, `logback-jackson`, `jackson-core`, `jackson-databind`; `logging` bundle → `["kotlin-logging", "logback-classic"]`. Kept `logback`, `handlebars`. |
| 3 | `build-logic/src/main/kotlin/kdiab.kotlin-base.gradle.kts` | Removed the two jackson constraint lines + jackson CVE comment; kept the `constraints {}` block and the `handlebars` pin (retargeted the comment to handlebars). |
| 4 | `.claude/rules/logging.md` | Added a JVM-backend note: native `JsonEncoder` schema mapping (epoch-millis `timestamp`, `formattedMessage`/`loggerName`/`threadName`, `mdc.Correlation-ID`); canonical ISO-8601 names remain the target for non-JVM (Pino/TS) services; Loki fed via OTLP so unaffected. |

## Not changed

- No `.kt` source (config + build metadata only).
- `kdiab-ui` (Pino, separate stack).
- Loki/OTEL config (fed via OTLP; not field-name-parsed).

## Verification handoff to build-and-test

- AC-1: `./gradlew :kdiab-<svc>:dependencies --configuration runtimeClasspath | grep -iE 'jackson|logback-contrib'` empty for all 8.
- AC-2/AC-7: `./gradlew check` green (all backends).
- AC-3: unit assertion on `mdc.Correlation-ID` (Q2=C).
- AC-4: handlebars constraint present; jackson constraints absent.
- AC-6: SBOM/Trivy have no jackson/logback-contrib (CI).
