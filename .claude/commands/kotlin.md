# Kotlin Reference — kdiab Platform

This skill is a reference for agents working on Kotlin code in the kdiab monorepo.
Use it when you need canonical API references, not guesses.

---

## kotlinx-datetime

**API docs**: https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/

### Imports
```kotlin
import kotlinx.datetime.*
```

### Key APIs

| What you need | Correct API |
|---|---|
| Current instant (UTC) | `Clock.System.now()` → `Instant` |
| Parse ISO-8601 string | `Instant.parse("2024-01-15T10:30:00Z")` |
| Instant → local datetime | `instant.toLocalDateTime(TimeZone.UTC)` |
| Local time of day | `localDateTime.time` → `LocalTime` |
| Construct LocalTime | `LocalTime(hour = 10, minute = 30)` |
| Compare LocalTime | `LocalTime` implements `Comparable` — use `<=`, `>=` |
| UTC timezone | `TimeZone.UTC` |
| System timezone | `TimeZone.currentSystemDefault()` |

### Common mistakes to avoid
- `Clock.System` is from `kotlinx.datetime` — **do not** import from `kotlin.time`
- `Instant.parse()` is on the `Instant` companion — no extra import needed
- In `domain/` and `application/` layers: use `kotlinx.datetime.Instant` and `LocalTime` — **never** `java.time.*`
- Infrastructure/persistence layers may use `java.time.*` only when Exposed ORM requires it

### Segment time lookup pattern (used in profile-aware services)
```kotlin
// Find last segment whose time <= refTime; fall back to last if none qualifies (midnight wrap)
private fun <T> lookupSegment(segments: List<T>, refTime: LocalTime, timeOf: (T) -> LocalTime, valueOf: (T) -> Double): Double {
    require(segments.isNotEmpty()) { "Segment list must not be empty" }
    return segments
        .filter { timeOf(it) <= refTime }
        .maxByOrNull { timeOf(it) }
        ?.let { valueOf(it) }
        ?: valueOf(segments.last())
}
```

---

## kotlin.uuid.Uuid

```kotlin
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
val id: Uuid = Uuid.parse("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
val str: String = id.toString()

// Conversion to java.util.UUID (for Exposed ORM only — never in domain/application)
val javaUuid: java.util.UUID = java.util.UUID.fromString(id.toString())
```

> Always add `@OptIn(ExperimentalUuidApi::class)` or configure it in `build.gradle.kts` via `compilerOptions { optIn.add("kotlin.uuid.ExperimentalUuidApi") }`.

---

## Exposed v1 (org.jetbrains.exposed.v1)

Exposed v1 changed package names. Use these imports:
```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
```

Suspend transaction pattern:
```kotlin
suspend fun <T> dbQuery(block: () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
```

---

## Ktor HttpClient for upstream calls

```kotlin
val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000L
        requestTimeoutMillis = 10_000L
        socketTimeoutMillis = 5_000L
    }
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 3)
        exponentialDelay(maxDelayMs = 8_000L)
    }
}
```

Forward JWT and correlation ID in every upstream request:
```kotlin
httpClient.get(url) {
    header(HttpHeaders.Authorization, authorization)   // forward unchanged
    header("X-Correlation-ID", correlationId)
}
```

---

## OpenAPI codegen — upstream client models

When a service needs DTOs matching an upstream service's API:
- **Do not** hand-write DTOs — generate them from the upstream `api/openapi.yaml`
- Use a `GenerateTask` in `build.gradle.kts` (see issue kdiab-ocg for full pattern)
- Generated model package convention: `org.javafreedom.kdiab.<service>.api.upstream.<upstream>`

$ARGUMENTS
