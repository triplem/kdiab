# Requirements: Gradle Build Convention Plugins + API-First Client Distribution

**Status**: Draft — awaiting approval
**Date**: 2026-05-31
**Author**: RequirementsAgent (rev 3 — expanded scope)

---

## 1. Problem Statement

Two problems addressed together:

**P1 — Build script duplication.** Every kdiab service has its own `build.gradle.kts` of 200–380 lines with ~80% identical content. Any shared convention change requires manual edits in 8–9 files.

**P2 — Ad-hoc inter-service client generation.** Services that call upstream APIs (analyze, nightscout, users) each contain hand-rolled or inline `GenerateTask` blocks pointing at peer services' `api/openapi.yaml` files by relative path. This means: (a) the generated code is not versioned as a proper artifact, (b) adding a new consumer requires copying the `GenerateTask` pattern, (c) two services (kdiab-users and kdiab-nightscout) don't even have server-side codegen despite having full `api/openapi.yaml` specs. One service (kdiab-nightscout) even has a hand-written Ktor HTTP client for kdiab-users because no generated client existed.

---

## 2. Stakeholders

| Role | Who |
|---|---|
| Developer / maintainer | triplem |
| Affected builds | kdiab-common, kdiab-measures, kdiab-profiles, kdiab-treatments, kdiab-carbs, kdiab-calc, kdiab-nightscout, kdiab-users, kdiab-analyze |

---

## 3. Functional Requirements

### 3.1 Version catalog bundles

Add the following `[bundles]` entries to `gradle/libs.versions.toml`. These replace lists of individual `libs.xxx` references in convention plugins with single `libs.bundles.xxx` calls.

| Bundle name | Contents |
|---|---|
| `ktor-server` | ktor-server-core, cio, content-negotiation, kotlinx-json, resources, openapi, swagger, call-id, call-logging, auth, auth-jwt, metrics, compression, auto-head-response, cors, hsts, default-headers, status-pages, di |
| `logging` | kotlin-logging, logback-classic, logback-json-classic, logback-jackson |
| `exposed` | exposed-core, exposed-jdbc, exposed-kotlin-datetime, exposed-json |
| `database` | (bundle: exposed) + postgresql, hikaricp, liquibase-core, kotlinx-datetime, kotlinx-coroutines-core |
| `test-unit` | kotlin:test-junit5, ktor-server-test-host, ktor-client-content-negotiation, mockk, h2 |
| `test-e2e` | kotest-runner-junit5, kotest-assertions-core, ktor-client-content-negotiation, ktor-server-test-host |

### 3.2 Create `buildSrc/`

`buildSrc/build.gradle.kts`:
```kotlin
plugins { `kotlin-dsl` }
```

`buildSrc/settings.gradle.kts` — re-declares the root version catalog (not auto-inherited):
```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
```

`gradle/openapi-defaults.properties` is **deleted** — defaults move into `kdiab.ktor-service`.

### 3.3 Convention Plugin: `kdiab.kotlin-base`

File: `buildSrc/src/main/kotlin/kdiab.kotlin-base.gradle.kts`

**Contains:**
- `kotlin("jvm")`
- `repositories { mavenCentral() }`
- `kotlin { jvmToolchain(21); compilerOptions { optIn.add("kotlin.uuid.ExperimentalUuidApi") } }`
- `detekt` plugin, config from `${rootDir}/config/detekt/detekt.yml`, baseline from `${rootDir}/config/detekt/baseline.xml` (per-service — `rootDir` resolves to the service's project directory in composite builds)
- Detekt task report formats (html, xml, txt, sarif, md) via `tasks.withType<Detekt>().configureEach {}`
- `tasks.named<Delete>("clean") { delete(projectDir.dir("bin")) }`

> **Baseline contract**: every service must keep `config/detekt/baseline.xml`. A new service creates an empty baseline before applying the plugin.

### 3.4 Convention Plugin: `kdiab.ktor-service`

File: `buildSrc/src/main/kotlin/kdiab.ktor-service.gradle.kts`

Applies `kdiab.kotlin-base`. Every Ktor-based HTTP service applies this plugin.

**Plugins added:**
- `alias(libs.plugins.kotlin.serialization)`
- `alias(libs.plugins.ktor)`
- `alias(libs.plugins.openapi.generator)` — **unconditional** (all 8 services now have server + client codegen)
- `alias(libs.plugins.kover)`
- `alias(libs.plugins.cyclonedx)`
- `alias(libs.plugins.asciidoctor)`
- `application`

**Dependencies:**
```kotlin
implementation(libs.bundles.ktor.server)
implementation(libs.bundles.logging)
implementation(libs.kotlinx.serialization.json)
testImplementation(libs.bundles.test.unit)
```

**Testing suites** (all three registered unconditionally):
- `test` (JUnit 5)
- `integrationTest` (JUnit 5, `shouldRunAfter test`, `src/integration-test/kotlin`)
- `e2eTest` (JUnit 5 + Kotest, `shouldRunAfter integrationTest`, `src/e2e-test/kotlin`)

Configuration inheritance: `integrationTestImplementation` and `e2eTestImplementation` each extend `implementation + testImplementation`.

**Task wiring:**
- `check.dependsOn(integrationTest, e2eTest)`
- `koverVerify.dependsOn(test, integrationTest, e2eTest)`
- `tasks.withType<Test>` logging: passed/skipped/failed, `showStandardStreams = true`, FULL exceptions

**Kover:**
- 80% minimum line coverage
- Unconditional exclusions: `*.api` package, `*ApplicationKt*`
- Per-service persistence exclusions remain in each service's `build.gradle.kts`

**OpenAPI — server codegen** (shared defaults):
- `typeMappings`: UUID → kotlin.String, date-time → kotlin.String
- `configOptions`: library=ktor, dateLibrary=java8, serializationLibrary=kotlinx_serialization
- `globalProperties`: models, apis, supportingFiles
- `templateDir`: `rootDir.parent + "/config/openapi-templates"` (assumes flat composite layout)

Service-specific overrides (`packageName`, `apiPackage`, `modelPackage`, `inputSpec`, `schemaMappings`, `importMappings`, `outputDir`) remain in each service's own `build.gradle.kts`.

**OpenAPI spec artifact exposure** (new, in plugin):

Each service exposes its `api/openapi.yaml` as a consumable Gradle artifact so that downstream services can resolve it by Maven coordinates without a `publishToMavenLocal` pre-step. No client code is generated by the upstream service — the generation happens in the downstream service that needs it.

```kotlin
val apiSpec by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec"))
    }
}

artifacts {
    add("apiSpec", layout.projectDirectory.file("api/openapi.yaml"))
}
```

The root `settings.gradle.kts` adds substitution rules so that `org.javafreedom.kdiab:kdiab-{service}-spec` resolves to the local project's `apiSpec` configuration without requiring `publishToMavenLocal`.

> **ADR required**: the exact composite build substitution mechanism (`dependencySubstitution` with outgoing variant matching, or a capability-based approach) is a non-trivial design decision. An ADR must be written before implementation of this section begins. See §9 Risks.

**Downstream consumer pattern** (in each downstream `build.gradle.kts`):

```kotlin
val measuresSpec by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class, "openapi-spec"))
    }
}

dependencies {
    measuresSpec("org.javafreedom.kdiab:kdiab-measures-spec")
}

val generateMeasuresModels by tasks.registering(GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set(provider { measuresSpec.singleFile.absolutePath })
    // packageName, outputDir, configOptions set per consumer
}
compileKotlin.dependsOn(generateMeasuresModels)
```

**Kotlin source sets:**
- `main`: adds `${buildDir}/generated/api/src/main/kotlin` (server stubs)

`compileKotlin.dependsOn(openApiGenerate)`

**processResources:** copies `api/openapi.yaml` to resources.

**Asciidoctor:** via `tasks.withType<AsciidoctorTask>().configureEach {}` (avoids ordering issues), `baseDirFollowsSourceFile()`, `notCompatibleWithConfigurationCache(...)` re-declared.

**Per-service required overrides:**

| Override | Why per-service |
|---|---|
| `group` | Unique package per service |
| `application { mainClass.set(...) }` | Unique entry point |
| `openApiGenerate { packageName, apiPackage, modelPackage, inputSpec, schemaMappings, importMappings }` | Unique per service |
| `kover { classes(...) }` persistence exclusions | Unique class patterns |

### 3.5 Convention Plugin: `kdiab.ktor-db-service`

File: `buildSrc/src/main/kotlin/kdiab.ktor-db-service.gradle.kts`

Applies `kdiab.ktor-service`. Adds:

```kotlin
implementation(libs.bundles.database)
// integrationTest suite additions:
implementation(libs.bundles.exposed)
implementation(libs.kotlinx.coroutines.core)
implementation(libs.kotlinx.datetime)
implementation(libs.h2)
```

> `liquibase-core` is in `libs.bundles.database` → added to `implementation` (main classpath). This supersedes the current kdiab-carbs behaviour (integrationTest only). The change is safe: Liquibase only runs when `APP_INIT_DATABASE=true`, which is never set in production containers.

### 3.6 Migrate all builds

#### Services with DB (apply `kdiab.ktor-db-service`)
`kdiab-measures`, `kdiab-profiles`, `kdiab-treatments`, `kdiab-carbs`, `kdiab-users`

Each retains: `group`, `mainClass`, `openApiGenerate` service-specific overrides, `kover` persistence exclusions.

#### Stateless services with server codegen (apply `kdiab.ktor-service`)
`kdiab-calc`, `kdiab-analyze`, `kdiab-nightscout`

Each retains: `group`, `mainClass`, `openApiGenerate` service-specific overrides.

#### Server codegen added (new for these two)
`kdiab-users` and `kdiab-nightscout` currently have `api/openapi.yaml` but no `openApiGenerate` server task. After migration, the plugin supplies the server codegen configuration — each service adds only the service-specific overrides (`packageName`, `inputSpec`, etc.).

#### Consumer services — GenerateTask paths replaced with spec artifacts

The inline `GenerateTask` blocks in downstream services keep their structure but replace hard-coded relative paths (`../kdiab-*/api/openapi.yaml`) with resolved spec artifact dependencies. The hand-written `UserSettingsClient.kt` in kdiab-nightscout is replaced by a generated client once kdiab-users exposes its spec.

| Consumer | Current path | Becomes |
|---|---|---|
| kdiab-analyze | `"../kdiab-measures/api/openapi.yaml"` etc. (3 blocks) | `measuresSpec("org.javafreedom.kdiab:kdiab-measures-spec")`, `inputSpec` resolved from config |
| kdiab-nightscout | `"../kdiab-*/api/openapi.yaml"` (4 blocks) + hand-written `UserSettingsClient.kt` | 5 spec configurations including `kdiab-users-spec`; `UserSettingsClient.kt` deleted |
| kdiab-users | `"../kdiab-*/api/openapi.yaml"` (3 blocks) | 3 spec configurations |

#### Shared library (apply `kdiab.kotlin-base`)
`kdiab-common`

Retains: `java-library` plugin, `kotlin.serialization`, `kover`, `maven-publish`, `publishing {}` block. **`maven-publish` and `publishing {}` must NOT move to `kdiab.kotlin-base`.**

#### Target line counts (non-blank lines after migration)

| Build | Plugin applied | Target |
|---|---|---|
| kdiab-common | `kdiab.kotlin-base` | ≤ 60 lines (publishing block ~25 lines) |
| kdiab-measures | `kdiab.ktor-db-service` | ≤ 30 lines |
| kdiab-profiles | `kdiab.ktor-db-service` | ≤ 30 lines |
| kdiab-treatments | `kdiab.ktor-db-service` | ≤ 30 lines |
| kdiab-carbs | `kdiab.ktor-db-service` | ≤ 30 lines |
| kdiab-users | `kdiab.ktor-db-service` | ≤ 30 lines |
| kdiab-calc | `kdiab.ktor-service` | ≤ 25 lines |
| kdiab-nightscout | `kdiab.ktor-service` | ≤ 50 lines (spec configurations + GenerateTask blocks retained) |
| kdiab-analyze | `kdiab.ktor-service` | ≤ 50 lines (spec configurations + GenerateTask blocks retained) |

---

## 4. Non-Functional Requirements

| # | Requirement | Acceptance test |
|---|---|---|
| NFR-1 | `./gradlew build` passes from repo root | CI green |
| NFR-2 | `./gradlew check` passes on all included builds | CI green |
| NFR-3 | No runtime behaviour changes (classpath additions from bundles are accepted) | Same task names, same artifacts |
| NFR-4 | No new configuration cache incompatibilities. Asciidoctor incompatibility is pre-existing and re-declared. | Same `--configuration-cache` warnings before and after |
| NFR-5 | `libs` version catalog accessible in `buildSrc` | Convention plugins compile |
| NFR-6 | Each service build file meets line budgets in §3.6 | `wc -l` check |
| NFR-7 | `./gradlew :kdiab-common:publishToMavenLocal` succeeds | Publish task passes |
| NFR-8 | Spec YAMLs resolvable in composite build without `publishToMavenLocal` pre-step | `./gradlew build` from root resolves all `kdiab-*-spec` artifacts from local projects |
| NFR-9 | kdiab-analyze, kdiab-nightscout, kdiab-users compile successfully using spec artifact dependencies — no hard-coded relative paths remain | Build succeeds; `GenerateTask.inputSpec` resolves via `kdiab-*-spec` configuration |

---

## 5. Out of Scope

- `kdiab-ui` TypeScript client generation (managed by `npm run api:generate`)
- Root `build.gradle.kts` refactoring
- Runtime behaviour changes in any service
- Plugin or library version upgrades
- Replacing hand-written `*Routes.kt` in kdiab-users or kdiab-nightscout with generated route handlers (that is a separate story)

---

## 6. Constraints

- Composite build structure (`includeBuild(...)`) must remain intact
- `buildSrc` must explicitly re-declare the version catalog
- `buildSrc/build.gradle.kts` must apply `kotlin-dsl`
- `templateDir` uses `rootDir.parent + "/config/openapi-templates"` — assumes flat composite layout
- `maven-publish` and `publishing {}` stay in `kdiab-common/build.gradle.kts`
- `./gradlew build --no-parallel` must remain viable for low-RAM machines
- **Spec artifact resolution without publish**: the composite build mechanism for `kdiab-*-spec` artifacts MUST NOT require a `publishToMavenLocal` pre-step. The implementation approach (`dependencySubstitution` with variant matching, or a capability-based approach) is an open design decision requiring an ADR before implementation of §1366 begins.

---

## 7. Acceptance Criteria

- [ ] `buildSrc/` exists with three plugins: `kdiab.kotlin-base`, `kdiab.ktor-service`, `kdiab.ktor-db-service`
- [ ] `buildSrc/settings.gradle.kts` wires the root `libs.versions.toml`
- [ ] `gradle/libs.versions.toml` contains the six bundles defined in §3.1
- [ ] `gradle/openapi-defaults.properties` is deleted
- [ ] All 9 builds apply the appropriate plugin with no duplicated boilerplate
- [ ] `./gradlew build` succeeds from root
- [ ] `./gradlew build --no-parallel` succeeds
- [ ] `./gradlew check` passes on every included build
- [ ] `./gradlew :kdiab-nightscout:check` passes (e2eTest task exists but may have no sources)
- [ ] kdiab-users and kdiab-nightscout produce server stub code from their `api/openapi.yaml` as part of `compileKotlin`
- [ ] All 8 services expose an `apiSpec` outgoing configuration containing `api/openapi.yaml`
- [ ] `org.javafreedom.kdiab:kdiab-measures-spec` (and the other 7 spec coordinates) resolve from local composite build without `publishToMavenLocal`
- [ ] kdiab-analyze, kdiab-nightscout, kdiab-users `GenerateTask` blocks resolve `inputSpec` via `kdiab-*-spec` artifacts — no hard-coded relative paths remain
- [ ] kdiab-nightscout's hand-written `UserSettingsClient.kt` is removed and replaced by a `GenerateTask`-generated client driven by `kdiab-users-spec`
- [ ] `./gradlew :kdiab-measures:detektMain` passes
- [ ] `./gradlew :kdiab-measures:koverVerify` enforces 80% coverage with persistence exclusions
- [ ] `./gradlew :kdiab-measures:asciidoctor` produces HTML
- [ ] `./gradlew :kdiab-common:publishToMavenLocal` succeeds

---

## 8. Migration Plan

Single PR, all 9 builds migrated together to keep root `./gradlew build` green throughout.

**Story sequencing** (prerequisite ordering):

| Story | Description | Depends on |
|---|---|---|
| S1 | Add version catalog bundles to `libs.versions.toml` | — |
| S2 | Create `buildSrc/` with `kdiab.kotlin-base` plugin; migrate kdiab-common | S1 |
| S3 | Create `kdiab.ktor-service` plugin (server codegen defaults, `apiSpec` outgoing config, `processResources` copies spec); migrate kdiab-calc | S2 |
| S3a | Apply `kdiab.ktor-service` to kdiab-analyze and kdiab-nightscout (server codegen overrides; GenerateTask path migration deferred to S7/S9) | S3 |
| S4 | Create `kdiab.ktor-db-service` plugin; migrate kdiab-measures, profiles, treatments, carbs | S3 |
| S5 | ADR: design OpenAPI spec artifact exposure in Gradle composite builds (`apiSpec` outgoing configuration + `dependencySubstitution` in root `settings.gradle.kts`) | S3 |
| S6 | Add substitution rules to root `settings.gradle.kts` for all 8 `kdiab-*-spec` coordinates; verify spec resolves from composite build | S5 |
| S7 | Migrate kdiab-analyze `GenerateTask` blocks: replace hard-coded `inputSpec` paths with resolved `kdiab-*-spec` artifact dependencies | S6 |
| S8 | Add server codegen (service-specific `openApiGenerate` overrides) to kdiab-users and kdiab-nightscout; migrate kdiab-users `GenerateTask` blocks to spec-artifact deps | S4, S6 |
| S9 | Migrate kdiab-nightscout `GenerateTask` blocks to spec-artifact deps; remove hand-written `UserSettingsClient.kt`; generate replacement client from `kdiab-users-spec`; verify API surface parity against spec file | S6 |
| S10 | Delete `gradle/openapi-defaults.properties`; run full build verification (`./gradlew build --no-parallel`, `check`, `asciidoctor`, `publishToMavenLocal`) | S7, S8, S9 |

**CI validation before merge:**
- `./gradlew build` and `./gradlew build --no-parallel`
- `./gradlew check --no-parallel` (RAM-constrained runner)
- `./gradlew :kdiab-common:publishToMavenLocal`
- `./gradlew :kdiab-measures:asciidoctor`

---

## 9. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Spec artifact composite build resolution (`apiSpec` outgoing config + `dependencySubstitution` variant matching) has no established pattern in the codebase | High | High — blocks S6+ | ADR (S5) must be written and approved before S6 begins |
| `buildSrc` version catalog wiring wrong — `libs` not accessible | Medium | High | Verify in first compile of S2 |
| kdiab-nightscout `UserSettingsClient.kt` deletion breaks something the generated client doesn't cover | Medium | Medium | Compare hand-written and generated client method signatures before deleting |
| kdiab-carbs Liquibase classpath change (integrationTest-only → implementation) causes unexpected behaviour | Low | Low | Guarded by `APP_INIT_DATABASE` flag; no production change |
| `publishToMavenLocal` broken after kdiab-common migration | Low | High | Explicit acceptance criterion in §7 |
