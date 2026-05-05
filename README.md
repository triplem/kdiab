# kdiab

A Type 1 Diabetes (T1D) management platform consisting of four services in a single monorepo.

| Service | Description |
|---|---|
| **kdiab-measures** | Health measurement tracking — CGM, BGM, blood pressure, weight, pulse |
| **kdiab-profiles** | Insulin pump basal profile management |
| **kdiab-treatments** | Treatment event tracking — bolus, basal, carbs, corrections |
| **kdiab-analyze** | Stateless BFF: aggregates all three services into a unified analytics dashboard |

## Prerequisites

- **JDK 21** (required by all Kotlin backends)
- **Node.js LTS + npm** (required by all React frontends)
- **Docker** or **Podman** with the Compose plugin

## Quick Start

```bash
# Copy the example env file and fill in secrets
cp .env.example .env

# Start the full platform (all 4 services + Keycloak + PostgreSQL)
docker compose up --build

# Optionally include pgAdmin (database browser at http://localhost:5050)
docker compose -f docker-compose.yml -f docker-compose.pgadmin.yml up --build
```

Navigate to http://localhost:3000 for the gateway (all UIs accessible under `/measures/`, `/profiles/`, `/treatments/`, `/analyze/`).

## Build Commands

### Monorepo Gradle build (root)

The monorepo is a [Gradle composite build](https://docs.gradle.org/current/userguide/composite_builds.html).
All four service backends and the `kdiab-ui` gateway frontend are orchestrated from the root:

```bash
./gradlew build              # Build all backends (Gradle) + kdiab-ui frontend (npm)
./gradlew check              # Build + run all tests, Detekt, Kover across all services
./gradlew buildBackends      # Gradle builds only (skip npm frontend)
./gradlew buildFrontend      # kdiab-ui npm build only (skip Gradle backends)
./gradlew build --no-parallel  # Sequential build (saves RAM on small machines)
```

### Docker images

```bash
./gradlew dockerBuild        # Build all JARs then build all kdiab Docker images
./gradlew dockerClean        # Stop containers, remove local images and volumes (DB reset)
```

`dockerBuild` automatically runs `buildBackends` first so JARs are ready. Images are single-stage (JRE-only, no build toolchain). For Podman, `./gradlew dockerBuild` works the same way via `podman compose`.

### Per-service backend (Kotlin/Ktor)

```bash
cd kdiab-<service>
./gradlew build              # Compile + package
./gradlew check              # All tests (unit + integration + e2e) + Detekt + Kover
./gradlew run                # Run locally (needs Postgres + Keycloak)
./gradlew test               # Unit tests only
./gradlew integrationTest    # Integration tests only (H2 in-memory)
./gradlew e2eTest            # E2E tests only
```

### Per-service frontend (React/TypeScript)

```bash
cd kdiab-<service>/frontend
npm install
npm run api:generate             # Regenerate TypeScript client from openapi.yaml
npm run dev                      # Dev server
npm run build                    # Production build
npm run lint                     # ESLint
```

### Standalone per-service stack

Each service ships its own `docker-compose.yml` for isolated development:

```bash
cd kdiab-<service>
docker compose up --build        # Service + its own Keycloak + Postgres
docker compose down -v           # Tear down and wipe volumes
```

## Service URLs

### Full platform (root compose)

| Component | URL |
|---|---|
| Gateway (all UIs) | http://localhost:3000 |
| kdiab-measures frontend | http://localhost:3004 (also `/measures/`) |
| kdiab-measures backend / Swagger | http://localhost:8080 / http://localhost:8080/swagger |
| kdiab-profiles frontend | http://localhost:3001 (also `/profiles/`) |
| kdiab-profiles backend / Swagger | http://localhost:8082 / http://localhost:8082/swagger |
| kdiab-treatments frontend | http://localhost:3002 (also `/treatments/`) |
| kdiab-treatments backend / Swagger | http://localhost:8083 / http://localhost:8083/swagger |
| kdiab-analyze frontend | http://localhost:3003 (also `/analyze/`) |
| kdiab-analyze backend / Swagger | http://localhost:8084 / http://localhost:8084/swagger |
| Keycloak Admin | http://localhost:8081 (admin / from `.env`) |
| pgAdmin | http://localhost:5050 — opt-in via `docker-compose.pgadmin.yml` |

### Per-service standalone compose

| Component | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger |
| Keycloak Admin | http://localhost:8081 |

## Test Accounts

All test accounts use password `password`.

| Username | Role | Glucose unit | Notes |
|---|---|---|---|
| `sarah` | PATIENT | mg/dL | |
| `mike` | PATIENT | mmol/L | |
| `dr_house` | DOCTOR | — | Allowed patient: sarah |
| `dr_cameron` | DOCTOR | — | Allowed patient: mike |
| `admin` | ADMIN | — | |

## Environment Variables

Copy `.env.example` to `.env` before running the full platform:

```
POSTGRES_PASSWORD=<strong secret>
KEYCLOAK_ADMIN_PASSWORD=<strong secret>
```

Frontend containers resolve the following variables at startup via `envsubst`:

| Variable | Description |
|---|---|
| `KEYCLOAK_URL` | Browser-facing Keycloak origin (used in Content-Security-Policy) |
| `BACKEND_URL` | Internal address nginx uses to proxy `/api/*` to the backend |

## Documentation

- [How to Build and Run](docs/how-to-run.adoc)
- [Platform Architecture](docs/architecture.adoc)
- [Keycloak Setup](docs/keycloak-setup.adoc)
- [Deployment Guide](docs/deployment.adoc)
- [T1D Glossary](docs/glossary.adoc)
- [Architecture Decision Records](docs/adr/)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow, branching strategy, and quality gate requirements.

## Security

See [SECURITY.md](SECURITY.md) for the vulnerability disclosure policy.
