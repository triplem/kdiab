# kdiab-common — Agent Context

Shared Kotlin library. Not a deployable service — included by all backend services via a composite Gradle build.
See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.common`

## What It Provides

### Domain

| File | Contents |
|---|---|
| `domain/model/Role.kt` | `Role` enum: `PATIENT`, `DOCTOR`, `ADMIN` |
| `domain/model/GlucoseUnits.kt` | `GlucoseUnit` values: `mg/dL`, `mmol/L` |
| `domain/model/AuditLog.kt` | `AuditLog` domain model for agent action logging |
| `domain/exception/DomainExceptions.kt` | `AuthenticationException`, `AuthorizationException`, `ResourceNotFoundException`, `BusinessValidationException`, `ConflictException` |
| `domain/PersistenceConstants.kt` | Shared DB column length constants |
| `domain/repository/AuditLogRepository.kt` | Port for audit log persistence |

### Plugins (installed by each service's `module()` via `CommonPlugins`)

| Plugin | What it does |
|---|---|
| `Security.kt` | JWT/JWKS auth, extracts `UserPrincipal` (userId, roles, allowedPatients, timezone) |
| `StatusPages.kt` | Maps domain exceptions → HTTP status codes (401/403/404/422/409) |
| `Logging.kt` | X-Correlation-ID extraction/generation → SLF4J MDC as `Correlation-ID` |
| `Health.kt` | `/healthz` (liveness) and `/readyz` (readiness — configurable probe) |
| `CircuitBreaker.kt` | Lightweight coroutine-safe circuit breaker (threshold=5, resetTimeout=30s) |
| `RateLimit.kt` | Token-bucket rate limiter per user/IP |
| `SecurityHeaders.kt` | CSP, X-Frame-Options, X-Content-Type-Options, HSTS |
| `Metrics.kt` | Micrometer + Prometheus `/metrics` endpoint |
| `Tracing.kt` | OpenTelemetry span propagation |
| `Cors.kt` | CORS configuration via `CORS_ALLOWED_ORIGINS` env var |
| `HttpClientDefaults.kt` | Shared Ktor CIO HttpClient with timeouts + retry |
| `ContentNegotiation.kt` | kotlinx.serialization JSON |
| `RouteUtils.kt` | `checkReadAccess`/`checkWriteAccess` helpers for route handlers |
| `AuditRoutes.kt` | Internal audit log endpoints |
| `ErrorResponse.kt` | Serializable error response body: `{"code":"...", "message":"..."}` |

## CircuitBreaker API

```kotlin
val cb = CircuitBreaker(name = "upstream-service")  // threshold=5, resetTimeout=30_000ms
cb.execute { httpClient.get("http://upstream/api/resource") }
// throws CircuitBreakerOpenException when OPEN
```

Log signature on state change: `circuit_breaker service=<name> state=OPEN|HALF_OPEN|CLOSED`

## Adding a New Plugin

1. Create `plugins/MyPlugin.kt` in `kdiab-common`
2. Add its installation to `CommonPlugins.kt` (or install directly in the service's `module()` if service-specific)
3. Add any new domain exceptions to `DomainExceptions.kt`
4. Update `StatusPages.kt` to map the new exception if needed
