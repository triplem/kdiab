# Code Summary — logback-jsonencoder

## What changed (11 files, feature branch `chore/1556-logback-jsonencoder`)

- **8 × `logback.xml`** — encoder swapped to native `ch.qos.logback.classic.encoder.JsonEncoder`
  (one line replacing a 9-line block). Verified: all 8 now contain exactly one `JsonEncoder`; the
  `STDOUT` appender, `${LOG_LEVEL:-INFO}` root, and Ktor `Application` logger are untouched.
- **`gradle/libs.versions.toml`** — dropped `logback-contrib` + `jackson` versions and the four
  `logback-json-classic` / `logback-jackson` / `jackson-core` / `jackson-databind` libraries; `logging`
  bundle reduced to `["kotlin-logging", "logback-classic"]`.
- **`build-logic/.../kdiab.kotlin-base.gradle.kts`** — removed the two jackson constraints + CVE
  comment; **kept** the `handlebars` constraint (CVE-2026-55760).
- **`.claude/rules/logging.md`** — reconciled the mandatory-fields table with the JVM `JsonEncoder`
  schema; canonical ISO-8601 names retained as the non-JVM target; noted Loki is OTLP-fed (unaffected).

## Static verification done at code-gen time

| Check | Result |
|---|---|
| Dangling refs to removed aliases in any `*.gradle.kts` | none |
| jackson / logback-contrib refs left in `libs.versions.toml` + `build-logic/` | none (only handlebars) |
| handlebars constraint retained | yes (line 25) |
| `logging` bundle wired via `bundle("logging")` in the ktor-service convention plugin | yes → propagates to all 8 |
| `.kt` source changed | none (config/build-metadata only) |

## Deferred / follow-up

- **Loki (#1023)** — no in-repo change needed (OTLP-fed). If a stdout-JSON parser is ever added,
  it must use the new keys. Captured as a note on #1023.
- **Runtime confirmation of `mdc.Correlation-ID`** — unit assertion now; full runtime check via CI/e2e
  (Q2 = C).

## Status

Code complete on the feature branch. Authoritative verification (`./gradlew check` + the AC-1
runtime-classpath grep across all 8) is the build-and-test stage. Commit + PR (`Closes #1556`,
merge-commit) is the delivery step after build-and-test passes.
