# kdiab-common

Shared Kotlin library for the kdiab T1D management platform.

## What it does

Provides cross-cutting concerns shared by all kdiab backend services:

- **Domain types** — `Role` enum (PATIENT, DOCTOR, ADMIN), `DomainExceptions` hierarchy (`AuthenticationException`, `AuthorizationException`, `ResourceNotFoundException`, `BusinessValidationException`, `ConflictException`)
- **Ktor plugins** — `configureSecurity` (JWT/JWKS auth, `UserPrincipal` extraction), `configureLogging` (X-Correlation-ID tracing via MDC), `configureStatusPages` (domain exception to HTTP status mapping), `configureCommonPlugins` (calls all of the above)
- **UserPrincipal** — carries `userId: Uuid`, `roles: Set<Role>`, `allowedPatients: Set<Uuid>`; `canAccess(targetUserId)` checks self/admin/doctor authorization

## Usage

Services depend on this library via Gradle composite build:

```kotlin
// service/build.gradle.kts
dependencies {
    implementation(project(":kdiab-common"))
}
```

## Build

```bash
./gradlew build      # Build and test
./gradlew check      # Build + tests + detekt + kover
```

No Docker image — this is a library, not a deployable service.
