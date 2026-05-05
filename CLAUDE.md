# Project Instructions for AI Agents

This file provides guidance to Claude Code (claude.ai/code) for all services in this monorepo.
Service-specific details are in each service's own `CLAUDE.md`.

## Project Overview

**kdiab** is a T1D (Type 1 Diabetes) management platform consisting of four services:
- **kdiab-measures** — health measurement tracking (CGM, BGM, blood pressure, weight, pulse)
- **kdiab-profiles** — insulin pump basal profile management
- **kdiab-treatments** — treatment event tracking (bolus, basal, carbs, corrections, etc.)
- **kdiab-analyze** — stateless Backend-for-Frontend: aggregates data from all three services and provides a unified analytics dashboard (timeline, HbA1c, AGP, profiles)

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
The root `docker-compose.yml` starts the entire platform (all 4 services + Keycloak + PostgreSQL):
```bash
docker compose up --build        # Start everything
docker compose down -v           # Tear down and wipe all volumes
podman compose up --build        # Podman alternative
# Include pgAdmin (opt-in):
docker compose -f docker-compose.yml -f docker-compose.pgadmin.yml up --build
```

The database is automatically initialised by:
1. `config/postgres/01-init-databases.sh` — creates `kdiab-measures`, `kdiab-profiles`, `kdiab-treatments` databases
2. `liquibase-measures` / `liquibase-profiles` / `liquibase-treatments` — run schema migrations
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
./gradlew :backend:run               # Run backend (requires external Postgres + Keycloak)
./gradlew :backend:test              # Unit tests only
./gradlew :backend:integrationTest   # Integration tests
./gradlew :backend:e2eTest           # E2E tests
./gradlew :backend:check             # All tests + detekt + kover
./gradlew :backend:detektMain        # Run detekt linter only
./gradlew :backend:koverReport       # Generate code coverage report
./gradlew asciidoctor                # Build docs from docs/ into build/docs/asciidoc/
```

### Frontend (React/TypeScript)
```bash
cd frontend
npm install
npm run api:generate             # Regenerate TypeScript API client from openapi.yaml
npm run dev                      # Dev server (port 3000, proxies /api to backend on 8080)
npm run build                    # Generate API client + TS compile + Vite build
npm run lint                     # ESLint
npm run test                     # Vitest unit tests
npx playwright test              # E2E tests (requires running app)
```

### Service URLs

**Root compose (all services):**
| Service | URL |
|---|---|
| Keycloak Admin | http://localhost:8081 (admin / admin) |
| pgAdmin | http://localhost:5050 (opt-in via docker-compose.pgadmin.yml) |
| Gateway (all UIs) | http://localhost:3000 → /measures/ /profiles/ /treatments/ /analyze/ |
| kdiab-measures frontend | http://localhost:3004 (also via gateway /measures/) |
| kdiab-measures backend / Swagger | http://localhost:8080 / http://localhost:8080/swagger |
| kdiab-profiles frontend | http://localhost:3001 (also via gateway /profiles/) |
| kdiab-profiles backend / Swagger | http://localhost:8082 / http://localhost:8082/swagger |
| kdiab-treatments frontend | http://localhost:3002 (also via gateway /treatments/) |
| kdiab-treatments backend / Swagger | http://localhost:8083 / http://localhost:8083/swagger |
| kdiab-analyze frontend | http://localhost:3003 (also via gateway /analyze/) |
| kdiab-analyze backend / Swagger | http://localhost:8084 / http://localhost:8084/swagger |

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
The BFF receives a user JWT and forwards it unchanged to all upstream services. For this to work, the Keycloak client used to log in must have audience mappers for all four audiences (`analyze`, `measure`, `profile`, `treatment`). The root `config/keycloak-realm.json` configures the `kdiab-analyze-frontend` client with all four audience mappers so a single token is accepted by every upstream service.

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
                               # client has 4 audience mappers (bff, measure, profile, treatment).
  postgres/
    01-init-databases.sh       # Creates kdiab-measures, kdiab-profiles, kdiab-treatments databases.
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

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Parallel Agent Workflow

For non-trivial work, use the three-phase workflow: **Spec → Parallel → Merge**.

### Three Phases

```
Phase 1 — Spec      /spec <id> [id2 id3...]
Phase 2 — Parallel  /parallel [id1 id2...]     (or leave blank to pick up all specced ready issues)
Phase 3 — Merge     git worktree list           (inspect branches, merge to main)
```

### Slash Commands

| Command | What it does |
|---|---|
| `/spec <id> [id2...]` | Enters plan mode, explores code, writes an OpenSpec to each issue's `--design` field. Requires user approval before storing. |
| `/parallel [id1 id2...]` | Reads each issue's OpenSpec, spawns one Agent per issue in an isolated git worktree (all in parallel). Leave args blank to pick up all ready specced issues. |
| `/implement <id>` | Worker command — claims an issue, follows its OpenSpec, runs quality gates, commits, closes. Embedded by `/parallel` into each agent prompt; also callable directly for a single issue. |

### Rules for the Parallel Workflow

1. **Spec is mandatory before parallel.** Never run `/parallel` on an issue whose `--design` field is empty.
2. **One issue = one worktree = one branch.** Agents never share a worktree.
3. **Quality gates must be green before commit.** Agents may not skip `--no-verify` or bypass coverage checks.
4. **All Agent calls in a single message.** The orchestrator sends all parallel agents in one response — do not await between them.
5. **Worktree branches are named `<issue-id>` or similar.** Use `git worktree list` to inspect active agents.

### Example Session

```bash
# 1. Create a batch of issues
bd create --title="Add pagination to MeasureList" --type=feature
bd create --title="Add weight unit toggle" --type=feature

# 2. Spec them (enters plan mode, writes OpenSpec to each issue's design field)
/spec kdiab-abc kdiab-def

# 3. Spawn parallel agents — each works in its own git worktree
/parallel kdiab-abc kdiab-def

# 4. Inspect results and merge branches
git worktree list
git merge kdiab-abc
git merge kdiab-def
git worktree prune
```

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->
