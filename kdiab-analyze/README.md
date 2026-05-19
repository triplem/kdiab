# kdiab-analyze

Stateless Backend-for-Frontend (BFF) service for the kdiab T1D management platform.

## What it does

Aggregates data from kdiab-measures, kdiab-profiles, and kdiab-treatments into a unified analytics dashboard. Computes HbA1c estimates (DCCT formula), time-in-range (TIR) breakdowns, ambulatory glucose profiles (AGP), and device usage statistics. Has no database — purely a read-time aggregator.

Forwards the user's JWT unchanged to all upstream services. The Keycloak client must have audience mappers for all six backend services.

## Local development

```bash
# Start BFF + Keycloak (upstream services run separately)
docker compose up --build

# Run tests
./gradlew test

# Run all checks (tests + detekt + kover)
./gradlew check
```

## URLs

| Resource | URL |
|---|---|
| API | http://localhost:8084/api/v1 |
| Swagger UI | http://localhost:8084/swagger |
| Keycloak Admin | http://localhost:8085 |

## Key environment variables

| Variable | Default | Description |
|---|---|---|
| `JWT_AUDIENCE` | `analyze` | Expected JWT audience |
| `MEASURES_URL` | `http://localhost:8080` | kdiab-measures base URL |
| `PROFILES_URL` | `http://localhost:8082` | kdiab-profiles base URL |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments base URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3003` | Allowed CORS origins |

## API spec

See [`api/openapi.yaml`](api/openapi.yaml) for the full OpenAPI 3.1 specification.

## Analytics formulas

- **HbA1c** — DCCT: `(mean_glucose_mg_dL + 46.7) / 28.7`
- **TIR** — ADA/EASD thresholds: Very Low < 54, Below 54-70, Target 70-180, Above 180-250, High > 250 (all in mg/dL)
- **AGP** — p10/p25/p50/p75/p90 per UTC hour (0-23), sort-based linear interpolation
