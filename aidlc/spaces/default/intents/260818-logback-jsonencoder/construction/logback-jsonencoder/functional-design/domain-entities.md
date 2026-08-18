# Domain Entities — N/A

## Not applicable for this intent

This refactor (`requirements.md` #1556) introduces **no domain entities, data models, or persisted
structures**. It changes only the log-line **serialization mechanism** — a swap of the Logback
encoder plus the removal of the jackson build dependencies. No table, no Exposed model, no domain
class, no migration is added or altered.

## Configuration objects touched (for completeness)

The only "entities" involved are build/config artifacts, fully specified in
[`business-logic-model.md`](./business-logic-model.md):

| Config object | File | Change |
|---|---|---|
| Logback `STDOUT` appender encoder | 8 × `kdiab-*/src/main/resources/logback.xml` | `LayoutWrappingEncoder`+`JsonLayout`+`JacksonJsonFormatter` → `JsonEncoder` |
| Version-catalog logging entries + `logging` bundle | `gradle/libs.versions.toml` | drop logback-contrib + jackson libs/versions; bundle → `["kotlin-logging","logback-classic"]` |
| Gradle jackson version constraints | `build-logic/.../kdiab.kotlin-base.gradle.kts` | remove jackson pins; keep handlebars |

No domain modelling is required; `code-generation` operates directly on the files above.
