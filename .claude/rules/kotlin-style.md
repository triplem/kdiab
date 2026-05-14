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
