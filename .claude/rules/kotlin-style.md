# Rule: Kotlin Style

## Tooling

**Gradle (Kotlin DSL — preferred for Kotlin projects):**
```kotlin
// build.gradle.kts — required plugins
id("io.gitlab.artefacts.detekt") version "1.23.7"
id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
```

**Maven (if the project uses Maven):**
```xml
<plugin>
    <groupId>com.github.ozsie</groupId>
    <artifactId>detekt-maven-plugin</artifactId>
    <version>1.23.7</version>
    <executions>
        <execution>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <config>config/detekt.yml</config>
            </configuration>
        </execution>
    </executions>
</plugin>
```

CI must fail on any detekt or ktlint violation.

## Idioms

- Prefer `val` over `var`. A `var` requires justification.
- Prefer `when` over `if-else if` chains.
- Use `apply`, `let`, `run`, `also`, `with` for scope functions — use the right one:
  - `let` — transform a nullable value
  - `apply` — configure an object, return itself
  - `also` — side effect, return the receiver
  - `run` — compute a result within a lambda
- Use `object` for singletons, not companion objects with a private constructor.
- Use `companion object` only for factory methods and constants.

## Null Safety

```kotlin
// Chained null handling
val display = user?.address?.city?.uppercase() ?: "Unknown"

// Require with message (for programmer-error nulls)
val userId = requireNotNull(payload["sub"]) { "JWT missing 'sub' claim" }

// Early return on null
val user = repo.findById(id) ?: return Result.NotFound(id)
```

## Coroutines Style

```kotlin
// Repository interface: suspend functions
interface UserRepository {
    suspend fun findById(id: UserId): User?
    suspend fun save(user: User): User
}

// Service: structured concurrency
suspend fun getUserDashboard(id: UserId): Dashboard = coroutineScope {
    val user = async { userRepo.findById(id) ?: throw EntityNotFoundException("User $id") }
    val stats = async { statsRepo.findByUserId(id) }
    Dashboard(user.await(), stats.await())
}

// Background task
CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
    // long-running background work
}
```

## Detekt Configuration

```yaml
# config/detekt.yml
style:
  MagicNumber:
    active: true
    ignoreNumbers: ['-1', '0', '1', '2']
  MaxLineLength:
    maxLineLength: 120
complexity:
  LongMethod:
    threshold: 20
  LongParameterList:
    threshold: 5
```

## ktlint

Follow the standard Kotlin coding conventions. Key points:
- 4-space indent (no tabs)
- Opening brace on same line
- No trailing whitespace
- Single empty line between top-level declarations

## Gradle: Configuration-cache-safe `doLast` blocks

Never capture Gradle mutable objects (`ProjectLayout`, `Property<T>`, `Project`, `DirectoryProperty`) inside a `doLast` closure — the configuration cache serialises lambdas and fails if they capture non-serialisable Gradle types.

**Pattern**: resolve all paths and values to plain `String` or `java.io.File` at configuration time (inside the task config block, before `doLast`), then capture only those in the closure. Use `java.io.File` directly for filesystem operations — not `project.fileTree()`, which resolves via `Project`.

```kotlin
// Good — captures a String, not a Gradle object
val generatedKtSrcDir = outputDir.get() + "/src/main/kotlin"
doLast {
    java.io.File(generatedKtSrcDir).walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { /* ... */ }
}

// Bad — captures layout (ProjectLayout), not serialisable
doLast {
    fileTree("${layout.buildDirectory.get()}/generated/...")
        .forEach { /* ... */ }
}
```

## Gradle: Root project `repositories` block

A root `build.gradle.kts` that applies a plugin directly (e.g. `org.asciidoctor.jvm.convert`) **must** declare its own `repositories { mavenCentral() }` block. Plugin transitive dependencies (JRuby, AsciidoctorJ, etc.) are resolved in the root project scope; without a repository declaration at that level, they cannot be resolved even if every subproject has `mavenCentral()`.

```kotlin
// build.gradle.kts (root)
plugins {
    id("org.asciidoctor.jvm.convert") version "4.0.5"
}

repositories {        // ← required — subproject repos do not apply here
    mavenCentral()
}
```

**Why**: PR #872 fixed a long-standing CI failure (`Cannot resolve external dependency org.asciidoctor:asciidoctorj:2.5.7 because no repositories are defined`) caused by this missing block.

## Gradle: Never patch generated-code compiler warnings in build scripts

Do not suppress compiler warnings from OpenAPI-generated (or any code-generated) sources by programmatically patching files in `doLast` actions or injecting `@file:Suppress` annotations at build time. This approach:

- Creates fragile build-time mutation that breaks on each regeneration
- Adds unnecessary configuration-cache complexity
- Obscures the root cause (a generator template issue)

**Instead**: open a tracking issue for the generator template fix and accept the warnings as noise until the template is updated. PR #874 was reverted for exactly this reason.
