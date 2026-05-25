# Project Instructions for AI Agents

This file provides guidance to Claude Code (claude.ai/code) for all services in this monorepo.

All agent work is logged to `~/.claude/kdiab-sessions/<session_id>.jsonl` for traceability.

## Project Overview

**kdiab** is a T1D (Type 1 Diabetes) management platform — a monorepo of nine components:
- **kdiab-measures** — health measurement tracking (CGM, BGM, blood pressure, weight, pulse)
- **kdiab-profiles** — insulin pump basal profile management
- **kdiab-treatments** — treatment event tracking (bolus, basal, carbs, corrections, etc.)
- **kdiab-analyze** — stateless Backend-for-Frontend: aggregates data from all services into a unified analytics dashboard (timeline, HbA1c, AGP, profiles)
- **kdiab-carbs** — food / carbohydrate database and entry tracking
- **kdiab-calc** — stateless dose calculator: bolus recommendation from profile + CGM trend
- **kdiab-users** — user management: Keycloak-backed registration, settings, doctor-patient relationships
- **kdiab-nightscout** — Nightscout API v1 compatibility layer for AAPS, xDrip+, Juggluco
- **kdiab-common** — shared Kotlin library: domain types (`Role`, `DomainExceptions`), Ktor plugins (`configureSecurity`, `configureLogging`, `configureStatusPages`), `UserPrincipal`

Each service follows the same stack and architecture conventions. All commands below must be run from the service directory.

## Common Tech Stack

- **Backend**: Kotlin + Ktor, Gradle Kotlin DSL
- **Frontend**: React 19 + TypeScript + Vite
- **API Contract**: OpenAPI spec at `api/openapi.yaml` — single source of truth for code generation
- **Persistence**: PostgreSQL + Exposed ORM + HikariCP + Liquibase migrations
- **Auth**: Keycloak (JWT/JWKS)
- **Tooling**: Detekt, Kover, CycloneDX SBOM, AsciiDoc docs

## Commands

### Full Monorepo Build
```bash
./gradlew build              # Build all backends (Gradle) + kdiab-ui frontend (npm)
./gradlew check              # Build + run all tests, Detekt, Kover
./gradlew buildBackends      # Gradle builds only (no frontend)
./gradlew buildFrontend      # npm build only (kdiab-ui)
./gradlew build --no-parallel  # Sequential (saves RAM on small machines)
./gradlew dockerBuild        # Build all Docker images via docker compose
./gradlew dockerClean        # Stop containers, remove all local images and volumes
```
Gradle output is in each service's `build/` directory. Use `--info` or `--parallel` flags as needed.

> **Memory note (#613):** A full `./gradlew clean dockerBuild` launches one Kotlin compiler daemon per
> included build. On machines with ≤ 8 GB RAM, pass `--no-parallel` to serialise them:
> `./gradlew clean dockerBuild --no-parallel`

### Full Stack (Docker/Podman) — Root Compose
The root `docker-compose.yml` starts the entire platform (all services + Keycloak + PostgreSQL):
```bash
docker compose up --build        # Start everything
docker compose down -v           # Tear down and wipe all volumes
./podman-up.sh --build           # Podman: starts app + OTEL/Jaeger (recommended)
./podman-up.sh --pgadmin --build # Podman: also include pgAdmin (opt-in)
# Include pgAdmin with plain docker compose (opt-in):
docker compose -f docker-compose.yml -f docker-compose.otel.yml -f docker-compose.pgadmin.yml up --build
```

The database is automatically initialised by:
1. `config/postgres/01-init-databases.sh` — creates `kdiab-measures`, `kdiab-profiles`, `kdiab-treatments`, `kdiab-carbs` databases
2. `liquibase-measures` / `liquibase-profiles` / `liquibase-treatments` / `liquibase-carbs` — run schema migrations
3. `pg-seed` — inserts 30 days of realistic CGM, treatment, and profile data for `sarah` and `mike`

### Per-Service Stack
Each service has its own compose file for standalone development:
```bash
cd kdiab-<service>
docker-compose up --build        # Service + its own Keycloak + Postgres
docker-compose down -v
./manage-podman.sh start         # Podman alternative
./manage-podman.sh cleanup
# Include pgAdmin:
docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build
```

Instead of docker-compose/docker compose, podman compose can be used.

### Backend (Kotlin/Ktor)
```bash
./gradlew run               # Run backend (requires external Postgres + Keycloak)
./gradlew test              # Unit tests only
./gradlew integrationTest   # Integration tests
./gradlew e2eTest           # E2E tests
./gradlew check             # All tests + detekt + kover
./gradlew detektMain        # Run detekt linter only
./gradlew koverReport       # Generate code coverage report
./gradlew asciidoctor                # Build docs from docs/ into build/docs/asciidoc/
```

### Frontend (React/TypeScript)
All frontends are in `kdiab-ui` (unified SPA):
```bash
cd kdiab-ui
npm install
npm run api:generate             # Regenerate TypeScript clients from all openapi.yaml specs
npm run dev                      # Dev server (http://localhost:3005)
npm run build                    # Generate API clients + TS compile + Vite build
npm run lint                     # ESLint
npm run test                     # Vitest unit tests
npm run test:e2e                 # Playwright e2e tests (requires running app)
```

### Service URLs

**Root compose (all services):**
| Service | URL |
|---|---|
| Keycloak Admin | http://localhost:8081 (admin / from `.env`) |
| pgAdmin | http://localhost:5050 (opt-in via docker-compose.pgadmin.yml) |
| kdiab-ui (all frontends) | http://localhost:3005 |
| kdiab-measures backend / Swagger | http://localhost:8080 / http://localhost:8080/swagger |
| kdiab-profiles backend / Swagger | http://localhost:8082 / http://localhost:8082/swagger |
| kdiab-treatments backend / Swagger | http://localhost:8083 / http://localhost:8083/swagger |
| kdiab-analyze backend / Swagger | http://localhost:8084 / http://localhost:8084/swagger |
| kdiab-carbs backend / Swagger | http://localhost:8085 / http://localhost:8085/swagger |
| kdiab-calc backend / Swagger | http://localhost:8086 / http://localhost:8086/swagger |
| kdiab-nightscout backend / Swagger | http://localhost:8087 / http://localhost:8087/swagger |
| kdiab-users backend / Swagger | http://localhost:8088 / http://localhost:8088/swagger |

**Per-service compose (standalone):**
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api/v1
- Swagger UI: http://localhost:8080/swagger
- Keycloak Admin: http://localhost:8081

## Architecture

### API-First Design
`api/openapi.yaml` is the contract between frontend and backend. Both sides generate code from it:
- **Backend**: `openApiGenerate` Gradle task produces Ktor server stubs + model classes into `backend/build/generated/api/`. Runs automatically before `compileKotlin`.
- **Frontend**: `npm run api:generate` produces a TypeScript/Axios client into `frontend/src/api/generated/`. Runs automatically before `npm run build`.

When changing the API, update `api/openapi.yaml` first, then regenerate on both sides.

### Hexagonal Architecture (Ports and Adapters)
All services follow the same layered package structure:
```
adapters/inbound/web/       # HTTP layer: Ktor route handlers + mapper (API↔domain)
application/service/        # Business logic
domain/model/               # Pure domain model (no framework dependencies)
  Role.kt                   # PATIENT, DOCTOR, ADMIN
domain/repository/          # Port interfaces (not present in kdiab-analyze — no DB)
domain/exception/           # DomainExceptions: AuthenticationException, AuthorizationException,
                            # ResourceNotFoundException, BusinessValidationException, ConflictException
infrastructure/persistence/ # Adapter: Exposed ORM + PostgreSQL (not present in kdiab-analyze)
adapters/outbound/http/     # Ktor HTTP clients for upstream calls (kdiab-analyze only)
plugins/                    # Ktor plugin config
  Security.kt               # JWT/JWKS auth, UserPrincipal extraction
  StatusPages.kt            # Exception → HTTP status mapping
  Logging.kt                # X-Correlation-ID tracing via MDC
  ErrorResponse.kt          # Serializable error response body
```

### Authentication
JWT-based via Keycloak. `UserPrincipal` extracted from JWT carries:
- `userId: Uuid` (from `sub` claim)
- `roles: Set<Role>` — PATIENT, DOCTOR, ADMIN
- `allowedPatients: Set<Uuid>` — for doctors

`UserPrincipal.canAccess(targetUserId)`: self OR admin OR (doctor AND targetUserId in allowedPatients).

In tests, JWT uses HMAC256 symmetric signing (`jwt.test=true`, `jwt.secret` in config).

### Route Pattern
- One private function per endpoint
- Type-safe routing via generated `Paths` from OpenAPI spec (kdiab-analyze uses manual routing — no generated Paths)
- Access control checked at route entry via `checkReadAccess`/`checkWriteAccess` helpers
- Domain exceptions thrown instead of manual HTTP status codes — caught by `StatusPages`

### JWT Forwarding (kdiab-analyze)
The BFF receives a user JWT and forwards it unchanged to all upstream services. For this to work, the Keycloak client used to log in must have audience mappers for all eight audiences (`analyze`, `measure`, `profile`, `treatment`, `carbs`, `calc`, `users`, `nightscout`). The root `config/keycloak-realm.json` configures the `kdiab-analyze-frontend` client with audience mappers so a single token is accepted by every upstream service.

### Test Suites (Backend)
```
src/test/             # Unit tests (MockK, JUnit5, H2 in-memory)
src/integration-test/ # Integration tests (JUnit5, shouldRunAfter test)
src/e2e-test/         # E2E tests (Kotest, shouldRunAfter integrationTest)
```

### Frontend
React 19 SPA with OIDC auth (`react-oidc-context`), `@tanstack/react-query`, `react-hook-form` + `zod`. Feature-based structure under `src/features/`. Tests: Vitest + `@testing-library/react`; Playwright for E2E.

Roles are parsed from the JWT access token directly (Keycloak's OIDC profile doesn't reliably include `realm_access`).

### Code Quality
- **Detekt**: config at `config/detekt/detekt.yml`, baseline at `config/detekt/baseline.xml`. Lints `src/main/kotlin` only.
- **Kover**: 80% minimum coverage enforced. Excludes `ApplicationKt`, `DatabaseFactory`, and generated `api` package.
- **CycloneDX**: SBOM generation via plugin.
- **Conventional Commits**: All commits follow Angular format (`feat:`, `fix:`, `chore:`, `refactor:`, etc.). Semantic Release on `main` determines version bumps.
- **Correlation ID tracing**: Every request carries `X-Correlation-ID` (generated by frontend Axios interceptor, extracted/generated by backend `CallId` plugin). Bound to SLF4J MDC as `Correlation-ID`.

### Domain Conventions
- Use `kotlin.uuid.Uuid` and `kotlinx.datetime.Instant`/`LocalTime` in domain code. Avoid `java.time.*` and `java.util.UUID` in `domain/` and `application/`.
- No Users table — `userId` stored directly as UUID; identity comes from Keycloak JWT claims.

### Root Config Files
```
config/
  keycloak-realm.json          # Unified Keycloak realm "kdiab" used by root docker-compose.yml.
                               # Contains all clients, roles, and test users. The kdiab-analyze-frontend
                               # client has audience mappers for all backends (analyze, measure, profile, treatment, carbs, calc, users, nightscout).
                               # See config/keycloak-realm.README.md for the production vs test-data boundary.
  keycloak-realm.README.md     # Documents production config vs test-data boundary; explains why the realm
                               # is one file (Keycloak --import-realm does not support partial imports).
  keycloak-theme/              # Custom Keycloak login theme (mounted as /opt/keycloak/themes/kdiab).
    login/                     # login.ftl, template.ftl, theme.properties, resources/css/login.css,
                               # login-update-password.ftl, login-update-profile.ftl
  postgres/
    01-init-databases.sh       # Creates kdiab-measures, kdiab-profiles, kdiab-treatments, kdiab-carbs databases.
                               # Runs once on first Postgres startup via docker-entrypoint-initdb.d.
    02-seed-data.sql           # Seeds 30 days of CGM readings, treatments, and profiles for sarah
                               # and mike. Runs via the pg-seed container after all Liquibase migrations.
```

### Test Accounts (pre-seeded in Keycloak)
All use password `password`:
- `sarah` — PATIENT, glucose unit mg/dL
- `mike` — PATIENT, glucose unit mmol/L
- `dr_house` — DOCTOR (assigned to sarah)
- `dr_cameron` — DOCTOR (assigned to mike)
- `admin` — ADMIN role

### Seed Data (root compose only)
`config/postgres/02-seed-data.sql` inserts:
- **kdiab-measures**: 8640 CGM readings/user (5-min intervals, 30 days, sine-wave glucose curve) + 120 BGM readings/user
- **kdiab-treatments**: 3 bolus+carbs pairs/day/user + occasional correction boluses for sarah
- **kdiab-profiles**: one ARCHIVED + one ACTIVE profile for sarah, one ACTIVE profile for mike

## Service Details

### kdiab-measures

Root package: `org.javafreedom.kdiab.measures`

**Package structure:**
```
adapters/inbound/web/
  MeasureRoutes.kt         # Route handlers — uses generated Paths for type-safe routing
  MeasureMapper.kt         # Extension functions: API models ↔ domain models
application/service/
  MeasureService.kt
domain/model/
  Measure.kt               # Measure entity + MeasureType/Source/Status enums
domain/repository/
  MeasureRepository.kt
infrastructure/persistence/
  ExposedMeasureRepository.kt
  DatabaseFactory.kt
```

**Data flow:**
```
HTTP Request
  → MeasureRoutes (authenticate, checkReadAccess/checkWriteAccess)
  → MeasureMapper.toDomain()
  → MeasureService (business logic, throws domain exceptions)
  → MeasureRepository → ExposedMeasureRepository (suspendTransaction on Dispatchers.IO)
HTTP Response ← MeasureMapper.toApi() ← StatusPages
```

**Domain model (DB schema):**
```
measures table:
  id, user_id, measured_at, created_at
  type    (CGM | BGM | BLOOD_PRESSURE | WEIGHT | PULSE)
  source  (MANUAL | NIGHTSCOUT | GOOGLE_FIT | APPLE_HEALTH)
  data    (JSONB — structure varies by type, e.g. {"sgv": 120, "trend": "Flat"})
  status  (ACTIVE | ARCHIVED)
```

`MeasurePayload`: the `data` field is mapped to `kotlinx.serialization.json.JsonObject` via `schemaMappings` in `build.gradle.kts` so the generator produces properly serializable code. Database migrations managed by Liquibase.

---

### kdiab-treatments

Root package: `org.javafreedom.kdiab.treatments`

**Package structure:**
```
adapters/inbound/web/
  TreatmentRoutes.kt       # Route handlers — uses generated Paths for type-safe routing
  TreatmentMapper.kt       # Extension functions: API models ↔ domain models
application/service/
  TreatmentService.kt
domain/model/
  Treatment.kt             # Treatment entity + TreatmentType/TreatmentStatus enums
domain/repository/
  TreatmentRepository.kt
infrastructure/persistence/
  ExposedTreatmentRepository.kt
  DatabaseFactory.kt
```

**Domain model (DB schema):**
```
treatments table:
  id, user_id, treated_at, created_at
  type    (BOLUS | BASAL | CARBS | CORRECTION_BOLUS | COMBO_BOLUS | TEMP_BASAL |
           EXERCISE | NOTE | BG_CHECK | PUMP_SUSPEND | SITE_CHANGE | SENSOR_INSERT | INSULIN_CHANGE)
  data    (JSONB — structure varies by type, follows Nightscout conventions)
  status  (ACTIVE | ARCHIVED)
  notes   (TEXT, nullable)
```

`TreatmentPayload`: the `data` field is mapped to `kotlinx.serialization.json.JsonObject` via `schemaMappings`. Delete endpoint is restricted to DOCTOR and ADMIN roles; archive is available to all authorized users. Database migrations managed by Liquibase.

---

### kdiab-profiles

Root package: `org.javafreedom.kdiab.profiles`

**Package structure:**
```
adapters/inbound/web/   # ProfileRoutes, InsulinRoutes, ProfileMapper
application/service/    # ProfileService — business logic, owns state machine transitions
domain/model/           # Profile, Insulin, segment types, ProfileStatus enum
domain/repository/      # ProfileRepository, InsulinRepository
infrastructure/persistence/  # ExposedProfileRepository, etc.
```

**Profile state machine:**
- DRAFT → ACTIVE (activate)
- ACTIVE → ARCHIVED + new ACTIVE (copy-on-write on update or activation)
- PROPOSED → ACTIVE (patient accepts) or ARCHIVED (patient rejects)
- Active profiles are **immutable** — any update archives current and creates a new ACTIVE version linked via `previousProfileId`. Clients receive a **new profile ID** — local state references must be updated.

**Domain validation:** Clinical safety checks live in `Profile.validate()` in the domain model: max daily basal 150 U/day, ICR/ISF range checks, unit heuristics.

**Database note:** The `IDX_PROFILES_USER_ACTIVE` partial index (`WHERE status = 'ACTIVE'`) enforcing one active profile per user is defined with `dbms: postgresql` — not created in H2. Integration tests use H2 in-memory.

**Key ADRs:**
- ADR-015 — Copy-on-Write profiles
- ADR-016 — Doctor-Patient collaboration via PROPOSED status
- ADR-302 — No Users table (userId from JWT `sub`)
- ADR-303 — JWT/RBAC via Keycloak `realm_access.roles`

---

### kdiab-analyze

Root package: `org.javafreedom.kdiab.analyze`

**Ports:**
| Component | Standalone compose | Root compose |
|---|---|---|
| analyze-backend | 8084 | 8084 |
| Keycloak | 8085 | — (shared at 8081) |

**Environment variables:**
| Variable | Default | Description |
|---|---|---|
| `MEASURES_URL` | `http://localhost:8080` | kdiab-measures base URL |
| `PROFILES_URL` | `http://localhost:8082` | kdiab-profiles base URL |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments base URL |
| `JWT_AUDIENCE` | `analyze` | Expected JWT audience |
| `JWT_REALM` | `kdiab-analyze` | Keycloak realm name |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3003` | Allowed CORS origins |

In root compose, upstream URLs are internal Docker service names (`http://measures-backend:8080` etc.).

**API endpoints** (all under `/api/v1/users/{userId}/`, require Bearer JWT):
| Method | Path | Description |
|---|---|---|
| GET | `/users/{userId}/timeline` | Combined measures + treatments |
| GET | `/users/{userId}/analytics/hba1c` | HbA1c estimate + TIR |
| GET | `/users/{userId}/analytics/agp` | AGP hourly percentiles (24 buckets) |
| GET | `/users/{userId}/profiles/active` | Active/archived profiles |

All endpoints accept `from` and `to` query parameters (ISO-8601, required).

**Backend package structure:**
```
adapters/inbound/web/
  BffRoutes.kt           # 4 endpoints, manual routing (no generated Paths)
  BffMapper.kt           # Domain models → API response DTOs
adapters/outbound/http/
  MeasuresClient.kt      # Ktor CIO HttpClient → kdiab-measures
  ProfilesClient.kt      # Ktor CIO HttpClient → kdiab-profiles
  TreatmentsClient.kt    # Ktor CIO HttpClient → kdiab-treatments
application/service/
  TimelineService.kt     # Fetches measures + treatments in parallel (coroutineScope/async)
  AnalyticsService.kt    # HbA1c (DCCT formula), TIR zone counts, AGP percentile calc
  ProfilesService.kt     # Returns ACTIVE + ARCHIVED profiles
domain/model/
  Timeline.kt            # TimelineMeasure, TimelineTreatment, Timeline
  Analytics.kt           # TirBreakdown, Hba1cResult, AgpHourlyData, AgpResult, ProfilesResult
domain/exception/
  UpstreamException.kt   # Wraps upstream HTTP errors → 502 Bad Gateway
```

**Analytics formulas:**

*HbA1c* — DCCT formula, CGM readings only:
```
mean_glucose_mg_dL = average(sgv values)
HbA1c (%) = (mean_glucose_mg_dL + 46.7) / 28.7
```
If `glucose_unit` JWT claim is `mmol/L`, multiply each value by 18.0 before averaging. Returns `null` if no CGM readings exist in the timeframe.

*Time In Range (mg/dL thresholds):*
| Zone | Range | Field |
|---|---|---|
| Very Low | < 54 mg/dL | `veryLowCount` |
| Below | 54–70 mg/dL | `belowCount` |
| Target | 70–180 mg/dL | `inRangeCount` |
| Above | 180–250 mg/dL | `aboveCount` |
| High | > 250 mg/dL | `highCount` |

*AGP* — group CGM readings by UTC hour (0–23), compute p10/p25/p50/p75/p90 per bucket using sort-based linear interpolation. Returns 24 `AgpHourlyData` objects; buckets with no readings have `null` percentile values.

**Key design decisions:**
- No database — purely a read-time aggregator; safe to scale horizontally.
- No generated Paths — uses manual Ktor routing; aggregation logic doesn't map to generated controller stubs.
- Parallel upstream calls — `TimelineService` uses `coroutineScope { async {} }` to minimise latency.
- mmol/L conversion happens in `AnalyticsService` before HbA1c; AGP and TIR thresholds are always mg/dL.

**JWT Forwarding:** The service forwards the user's `Authorization: Bearer <token>` unchanged to all upstream services. The root compose `kdiab-analyze-frontend` Keycloak client has audience mappers for all backends — tokens are accepted everywhere. The standalone compose client only has the `analyze` audience; upstream services need a shared realm or separate configuration.

---

### kdiab-users

Root package: `org.javafreedom.kdiab.users`

**Package structure:**
```
adapters/inbound/web/
  UserRoutes.kt            # My profile + settings endpoints
  RegistrationRoutes.kt    # Self-registration (if enabled)
  DoctorPatientRoutes.kt   # Doctor-patient relationship management
  UserMapper.kt            # API ↔ domain mappers
application/service/
  UserService.kt           # Settings update with alarm validation
  RegistrationService.kt   # User creation via Keycloak Admin API
  DoctorPatientService.kt
domain/model/
  User.kt                  # User entity
  UserSettings.kt          # Glucose unit, timezone, alarm thresholds
  DoctorPatientRelation.kt
domain/repository/
  UserSettingsRepository.kt
  DoctorPatientRepository.kt
  IdentityProviderPort.kt  # Port for Keycloak Admin API operations
infrastructure/keycloak/
  KeycloakAdminClient.kt   # Keycloak Admin REST client
  KeycloakIdentityProviderAdapter.kt  # Implements IdentityProviderPort
infrastructure/persistence/
  ExposedUserSettingsRepository.kt
  ExposedDoctorPatientRepository.kt
  DatabaseFactory.kt
```

**Environment variables:**

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8088` | HTTP port |
| `JWT_AUDIENCE` | `users` | Expected JWT audience |
| `KEYCLOAK_ADMIN_URL` | — | Keycloak base URL for Admin API |
| `KEYCLOAK_REALM` | `kdiab` | Realm name |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `kdiab-users-service` | M2M client ID |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | — | M2M client secret (rotate post-deploy) |
| `SELF_REGISTRATION_ENABLED` | `false` | Whether public self-registration is allowed |
| `SELF_REGISTRATION_REQUIRES_APPROVAL` | `false` | Whether registrations need admin approval |
| `JDBC_URL` | — | PostgreSQL JDBC URL for `kdiab-users` database |
| `DB_USER` / `DB_PASSWORD` | — | Database credentials |

**Domain rules:**
- Alarm thresholds must satisfy `urgentHigh > high > low > urgentLow` (all four must be set or skipped).
- `urgentLow ≥ 40 mg/dL`; `urgentHigh ≤ 400 mg/dL`.
- Doctor-patient relationships control which patients a doctor can access via `UserPrincipal.canAccess()`.

---

### kdiab-nightscout

Root package: `org.javafreedom.kdiab.nightscout`

**Package structure:**
```
adapters/inbound/web/
  NightscoutRoutes.kt      # GET /api/v1/entries.json, POST /api/v1/entries.json
                           # GET /api/v1/treatments.json, POST /api/v1/treatments.json
                           # GET /api/v1/status.json
  NightscoutMapper.kt      # Nightscout format ↔ kdiab domain model
adapters/outbound/http/
  MeasuresClient.kt        # Ktor HTTP client → kdiab-measures
  TreatmentsClient.kt      # Ktor HTTP client → kdiab-treatments
  CircuitBreaker.kt        # Local circuit breaker for upstream calls
application/service/
  NightscoutService.kt     # Translates Nightscout requests to upstream calls
domain/model/
  NightscoutModels.kt      # Nightscout v1 data models (entries, treatments)
domain/exception/
  UpstreamException.kt
```

**Environment variables:**

| Variable | Default | Description |
|---|---|---|
| `MEASURES_URL` | `http://localhost:8080` | kdiab-measures base URL |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments base URL |
| `JWT_AUDIENCE` | `nightscout` | Expected JWT audience |
| `JWT_REALM` | `kdiab` | Keycloak realm name |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3005` | Allowed CORS origins |

**API version:** Implements **Nightscout API v1** (paths under `/api/v1/`). The README and any docs referring to "v3" are incorrect — the live implementation uses v1 paths.

**Compatible clients:** AAPS, xDrip+, Juggluco, and any app supporting Nightscout API v1.

---

## Issue Tracking

This project uses **GitHub Issues** for all task tracking.

```bash
gh issue list                        # List open issues
gh issue view <number>               # View issue details
gh issue create --title "..." --body "..."  # Create an issue
gh issue close <number>              # Close an issue
gh pr create                         # Open a pull request
gh pr list                           # List open PRs
```

Create an issue before writing code. Reference it in commit messages with `Closes #N` or `Refs #N`.

---

## Branch Naming Convention

```
<type>/<issue-number>-<short-description>
```

Types: `feature`, `fix`, `bug`, `chore`, `docs`, `refactor`

Examples:
- `feature/42-user-authentication`
- `fix/101-null-pointer-on-login`

Each user story gets **one feature branch**. Multiple agents use **separate worktrees** on the same branch.

---

## Commit Conventions

Follow [Conventional Commits](https://www.conventionalcommits.org/) (Angular preset for semantic-release):

```
<type>(<scope>): <short summary>

[optional body]

[optional footer: BREAKING CHANGE, Closes #n]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`

---

## Quality Gates (mandatory before any PR)

- [ ] All tests pass
- [ ] Unit test coverage ≥ 80%
- [ ] Linting passes (language-specific tool)
- [ ] SAST scan passes (no HIGH/CRITICAL findings unmitigated)
- [ ] OpenAPI spec valid (if API changes exist)
- [ ] No `TODO` / `FIXME` left in changed files (unless tracked as an issue)

---

## Audit Logging

Every agent action is appended to `~/.claude/kdiab-sessions/<session_id>.jsonl` (outside the repo):

```json
{"ts":"2026-05-14T10:00:00Z","agent":"RequirementsAgent","action":"challenge","target":"epic-001","verdict":"REVISE","reason":"missing non-functional requirements"}
```

Session files accumulate all entries for a session and are never committed to git.

## Rules Index

All rules live in `.claude/rules/`. They are automatically applied.

- `commit-conventions.md` — Conventional Commits
- `branching-strategy.md` — Branch naming & merge strategy
- `quality-gates.md` — Mandatory checks before PR
- `test-pyramid.md` — Unit / Integration / E2E / Contract ratios
- `solid-principles.md` — SOLID + Clean Code
- `api-design.md` — OpenAPI / REST best practices
- `security.md` — SAST, OWASP, secret hygiene
- `kotlin-style.md` — Kotlin idioms, detekt rules, Gradle & Maven
- `java-style.md` — Java 21+ idioms, Checkstyle/SpotBugs/PMD, Gradle & Maven
- `typescript-style.md` — TypeScript strict mode, ESLint
- `spring-boot.md` — Spring Boot patterns
- `react.md` — React patterns & hooks discipline
- `angular.md` — Angular patterns
- `dotnet.md` — .NET / C# patterns
- `logging.md` — Structured logging across all stacks
- `openapi.md` — OpenAPI spec discipline
- `agent-context.md` — Single root CLAUDE.md convention; PostToolUse hook audit logging pattern
- `github-issue-management.md` — Native sub-issues via GraphQL `addSubIssue`; epic documentation checklist (ADR, reference, ops guide, user guide); always assign issues to `@me`

## Skills Index

All skills live in `.claude/skills/`. Invoke with `/skill-name`.

### SDLC Workflow Skills
- `/gather-requirements` — elicit, challenge, and document requirements
- `/write-epics` — decompose requirements doc into epics
- `/write-stories` — decompose epic into user stories (assigns all issues to `@me`)
- `/implement` — implement a story on a feature branch/worktree
- `/implement-epic` — implement all stories under an epic in dependency order; discovers sub-issues, builds execution waves, delegates each story to `/implement`
- `/write-tests` — write tests for an implementation
- `/create-pr` — open a guided PR
- `/pr-reviewer` — review a GitHub PR against all project rules; classifies findings by BLOCKER/MAJOR/MINOR and optionally posts review comment
- `/release` — tag, changelog, publish
- `/create-adr` — propose and document an architecture decision
- `/challenge` — peer-review another agent's output

### Code Pattern Skills
- `/kotlin-patterns` — Kotlin idioms, coroutines, data classes
- `/java-patterns` — Java 21+ records, sealed classes, pattern matching, virtual threads
- `/typescript-patterns` — TS strict patterns, generics, utility types
- `/spring-boot-patterns` — controllers, services, repositories, configuration (Kotlin & Java)
- `/react-patterns` — hooks, context, component design
- `/angular-patterns` — modules, services, RxJS / signals
- `/dotnet-patterns` — C# patterns, DI, middleware
- `/openapi-patterns` — spec-first API design
- `/logging-kotlin` — structured logging with kotlin-logging / logback
- `/logging-java` — structured logging with SLF4J / @Slf4j / logback
- `/logging-typescript` — structured logging with pino / winston

### Specialist Review Skills
- `/doctor-t1d-review` — T1D specialist endocrinologist: clinical correctness, safety, medical workflow
- `/patient-t1d-review` — T1D patient with pump + CGM: usability, real-world fit, trust
- `/security-review` — Security specialist: OWASP Top 10, auth, secrets, injection
- `/qa-review` — QA/QS engineer: test coverage, acceptance criteria, edge cases, regression risk
- `/architect-review` — Software architect: hexagonal layers, SOLID, coupling, API design
- `/devops-review` — DevSecOps engineer: container hygiene, env config, CI/CD, supply chain
- `/ux-review` — UI/UX + accessibility: WCAG AA, ease of use, interaction design
- `/requirements-review` — Requirements engineer: completeness, testability, consistency, NFRs
- `/performance-review` — Performance engineer: query efficiency, bundle size, response times
- `/operations-review` — SRE/operations: observability, graceful degradation, runbooks
- `/technical-writer-review` — Technical writer: documentation clarity, in-app help text, audience fit
- `/challenge-all` — Run all 11 specialist perspectives against a single target; consolidated ACCEPT/REVISE/REJECT verdict

### Meta Skills
- `/audit` — review and summarise the audit log
- `/learn` — extract a reusable rule from a completed story
- `/domain-model` — build/update the project domain model
