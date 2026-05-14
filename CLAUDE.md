# Project Instructions for AI Agents

This file provides guidance to Claude Code (claude.ai/code) for all services in this monorepo.
Service-specific details are in each service's own `CLAUDE.md`.

All agent work is logged to `audit/` for traceability.

## Project Overview

**kdiab** is a T1D (Type 1 Diabetes) management platform — a monorepo of seven components:
- **kdiab-measures** — health measurement tracking (CGM, BGM, blood pressure, weight, pulse)
- **kdiab-profiles** — insulin pump basal profile management
- **kdiab-treatments** — treatment event tracking (bolus, basal, carbs, corrections, etc.)
- **kdiab-analyze** — stateless Backend-for-Frontend: aggregates data from all services into a unified analytics dashboard (timeline, HbA1c, AGP, profiles)
- **kdiab-carbs** — food / carbohydrate database and entry tracking
- **kdiab-calc** — stateless dose calculator: bolus recommendation from profile + CGM trend
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

### Full Stack (Docker/Podman) — Root Compose
The root `docker-compose.yml` starts the entire platform (all services + Keycloak + PostgreSQL):
```bash
docker compose up --build        # Start everything
docker compose down -v           # Tear down and wipe all volumes
podman compose up --build        # Podman alternative
# Include pgAdmin (opt-in):
docker compose -f docker-compose.yml -f docker-compose.pgadmin.yml up --build
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
The BFF receives a user JWT and forwards it unchanged to all upstream services. For this to work, the Keycloak client used to log in must have audience mappers for all six audiences (`analyze`, `measure`, `profile`, `treatment`, `carbs`, `calc`). The root `config/keycloak-realm.json` configures the `kdiab-analyze-frontend` client with all six audience mappers so a single token is accepted by every upstream service.

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
                               # client has 6 audience mappers (analyze, measure, profile, treatment, carbs, calc).
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

Every agent action is appended to `audit/agent-log.jsonl`:

```json
{"ts":"2026-05-14T10:00:00Z","agent":"RequirementsAgent","action":"challenge","target":"epic-001","verdict":"REVISE","reason":"missing non-functional requirements"}
```

Human decisions are logged to `audit/human-decisions.jsonl`.

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

## Skills Index

All skills live in `.claude/skills/`. Invoke with `/skill-name`.

### SDLC Workflow Skills
- `/gather-requirements` — elicit, challenge, and document requirements
- `/write-epics` — decompose requirements doc into epics
- `/write-stories` — decompose epic into user stories
- `/implement` — implement a story on a feature branch/worktree
- `/write-tests` — write tests for an implementation
- `/create-pr` — open a guided PR
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

### Meta Skills
- `/audit` — review and summarise the audit log
- `/learn` — extract a reusable rule from a completed story
- `/domain-model` — build/update the project domain model
