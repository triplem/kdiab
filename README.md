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

# Start the full platform (all 4 services + Keycloak + PostgreSQL + pgAdmin)
docker compose up --build
```

Navigate to http://localhost:3000 for the gateway (all UIs accessible under `/measures/`, `/profiles/`, `/treatments/`, `/analyze/`).

## Build Commands

### Monorepo build script

```bash
./build.sh                   # Build all backends + frontends
./build.sh --check           # Build + run all tests, Detekt, Kover
./build.sh --backend-only    # Gradle builds only
./build.sh --frontend-only   # npm builds only
./build.sh --no-parallel     # Sequential (saves RAM on small machines)
./build.sh --clean           # Stop containers, remove local images and volumes
```

Build logs land in `.build-logs/` per service.

### Per-service backend (Kotlin/Ktor)

```bash
cd kdiab-<service>
./gradlew :backend:build         # Compile + package
./gradlew :backend:check         # Tests + Detekt + Kover
./gradlew :backend:run           # Run locally (needs Postgres + Keycloak)
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
| pgAdmin | http://localhost:5050 (admin@kdiab.dev / admin) |

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
