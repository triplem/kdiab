# Startup Guide

## Prerequisites

-   Docker and Docker Compose
-   OR Podman and podman-compose

> **Database requirement**: The application enforces a partial unique index (`IDX_PROFILES_USER_ACTIVE`) that allows only one active profile per user. This index uses PostgreSQL-specific syntax (`WHERE status = 'ACTIVE'`) and is **not** applied to H2 (used in integration tests). **PostgreSQL is required for production deployments.** The provided `docker-compose.yml` already uses PostgreSQL.

## Quick Start

To start the full application (Backend + Frontend + Database + Keycloak), run:

```bash
# Build the backend JAR first (required for optimized Docker build)
./gradlew backend:build -x check

docker-compose up --build
# Or with podman:
# ./manage-podman.sh build && ./manage-podman.sh start
```

Access the services at:

-   **Frontend**: [http://localhost:3000](http://localhost:3000)
-   **Backend API**: [http://localhost:8080/api/v1](http://localhost:8080/api/v1) (proxied via frontend at [http://localhost:3000/api](http://localhost:3000/api))
-   **Swagger UI**: [http://localhost:8080/swagger](http://localhost:8080/swagger)
-   **Keycloak**: [http://localhost:8081](http://localhost:8081)

### Test Users
The application is pre-configured with several test accounts in Keycloak. The password for all accounts is `password`.

| Username | Role | Description |
| ---- | ---- | ---- |
| `sarah` | `PATIENT` | T1D Patient |
| `mike` | `PATIENT` | T1D Patient |
| `dr_house` | `DOCTOR` | Doctor (Allowed patients: sarah) |
| `dr_cameron` | `DOCTOR` | Doctor (Allowed patients: mike) |
| `admin` | `ADMIN` | Super Administrator |

## Production Configuration

The frontend container is environment-agnostic: no URLs are baked into the image. Nginx resolves the following variables at container startup via `envsubst`:

| Variable | Description | Default (local dev) |
| ---- | ---- | ---- |
| `KEYCLOAK_URL` | **Browser-facing** Keycloak origin. Included in the `Content-Security-Policy connect-src` directive so the OIDC client can reach the Identity Provider. Must be the URL the end-user's browser uses — not an internal service name. | `http://localhost:8081` |
| `BACKEND_URL` | Internal address nginx uses to proxy `/api/*` to the backend. Never exposed to the browser. | `http://backend:8080` |

Example production override:

```bash
KEYCLOAK_URL=https://auth.example.com \
BACKEND_URL=http://backend-service:8080 \
docker-compose up
```

Or via a `.env` file in the repository root:

```
KEYCLOAK_URL=https://auth.example.com
BACKEND_URL=http://backend-service:8080
```

> **Note**: Setting `KEYCLOAK_URL` incorrectly will cause the OIDC login flow to fail with a Content Security Policy violation in the browser console.

## Development Setup

### Backend
The backend is a Ktor application running on port 8080. It connects to Postgres and Keycloak.

### Frontend
The frontend is a React application running on port 3000 (mapped to Nginx port 80).
It serves the UI and proxies `/api` requests to the backend to avoid CORS issues.

## Data Persistence
Postgres data is persisted in a named volume `postgres_data`. To reset the database, run:

```bash
docker-compose down -v
# Or with podman:
# podman-compose down -v
```

Additional information can be found in the ADRs.