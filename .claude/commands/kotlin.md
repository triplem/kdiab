# Kotlin Reference — kdiab Platform

This skill is a reference for agents working on Kotlin code in the kdiab monorepo.
Use it when you need canonical API references, not guesses.

---

## kotlinx-datetime

**API docs**: https://kotlinlang.org/api/kotlinx-datetime/kotlinx-datetime/

> **Kotlin 2.1 / kotlinx-datetime 0.7+ split**: `Clock` and `Instant` moved to the Kotlin stdlib (`kotlin.time`). The `kotlinx.datetime` package still owns `LocalDate`, `LocalTime`, `LocalDateTime`, `TimeZone`, and conversion extensions.

### Imports (Kotlin 2.1 / kotlinx-datetime 0.7+)
```kotlin
import kotlin.time.Clock          // Clock.System.now() — stdlib
import kotlin.time.Instant        // Instant, Instant.parse() — stdlib
import kotlinx.datetime.*         // LocalTime, LocalDateTime, TimeZone, toLocalDateTime()
```

### Key APIs

| What you need | Correct API (Kotlin 2.1+) |
|---|---|
| Current instant (UTC) | `Clock.System.now()` → `kotlin.time.Instant` |
| Parse ISO-8601 string | `Instant.parse("2024-01-15T10:30:00Z")` — `kotlin.time.Instant` |
| Instant → local datetime | `instant.toLocalDateTime(TimeZone.UTC)` — returns `kotlinx.datetime.LocalDateTime` |
| Local time of day | `localDateTime.time` → `kotlinx.datetime.LocalTime` |
| Construct LocalTime | `LocalTime(hour = 10, minute = 30)` — `kotlinx.datetime.LocalTime` |
| Compare LocalTime | `LocalTime` implements `Comparable` — use `<=`, `>=` |
| UTC timezone | `TimeZone.UTC` — `kotlinx.datetime.TimeZone` |
| System timezone | `TimeZone.currentSystemDefault()` |

### Common mistakes to avoid
- `Clock` and `Instant` are from `kotlin.time` (stdlib) — **not** `kotlinx.datetime`
- `LocalTime`, `LocalDateTime`, `TimeZone` are from `kotlinx.datetime` — **not** `kotlin.time`
- In `domain/` and `application/` layers: use `kotlin.time.Instant` and `kotlinx.datetime.LocalTime` — **never** `java.time.*`
- Infrastructure/persistence layers may use `java.time.*` only when Exposed ORM requires it

### Segment time lookup pattern (used in profile-aware services)
```kotlin
// Find last segment whose time <= refTime; fall back to last if none qualifies (midnight wrap)
private fun lookupSegment(segments: List<IsfSegment>, refTime: LocalTime): Double {
    require(segments.isNotEmpty()) { "Segment list must not be empty" }
    return (segments.filter { parseTime(it.startTime) <= refTime }
        .maxByOrNull { parseTime(it.startTime) }
        ?: segments.last()).value
}

private fun parseTime(hhmm: String): LocalTime {
    val (h, m) = hhmm.split(":").map { it.toInt() }
    return LocalTime(h, m)
}
```

---

## Ktor

**API docs**: https://api.ktor.io/

### HttpClient for upstream calls

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

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

### Generated jvm-ktor clients (upstream codegen)

Upstream API clients are generated via openapi-generator (`kotlin` generator, `library=jvm-ktor`).
The generated `DefaultApi` wraps an `ApiClient`. Pass the shared engine + inject per-request headers:

```kotlin
// In adapter constructor
class MeasuresClient(private val httpClientEngine: HttpClientEngine, private val baseUrl: String)

// Per-call: create ApiClient with JWT + correlation ID
val apiClient = ApiClient(
    baseUrl = baseUrl,
    httpClientEngine = httpClientEngine,
    httpClientConfig = {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
    }
).apply { setBearerToken(authorization.removePrefix("Bearer ").trim()) }
val api = DefaultApi(apiClient)
```

### Non-2xx error handling with generated clients

```kotlin
import io.ktor.client.plugins.ResponseException

val result = try {
    api.listMeasures(userId = userId, page = page, size = PAGE_SIZE)
} catch (e: ResponseException) {
    val body = runCatching { e.response.bodyAsText() }.getOrNull()
    throw UpstreamException("measures", e.response.status.value,
        e.response.status.description, body, e.response.request.url.toString())
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

## OpenAPI codegen — upstream clients

Generated from upstream `api/openapi.yaml` using `kotlin` generator + `library=jvm-ktor`:
- Models in: `org.javafreedom.kdiab.<service>.api.upstream.<upstream>.models`
- API class in: `org.javafreedom.kdiab.<service>.api.upstream.<upstream>.apis.DefaultApi`
- Infrastructure in: `org.javafreedom.kdiab.<service>.api.upstream.<upstream>.infrastructure`

**Do not** hand-write upstream DTOs or HTTP client code — generate from the spec.

$ARGUMENTS
