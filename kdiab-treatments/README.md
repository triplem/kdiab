# kdiab-treatments

Treatment event tracking service for the kdiab T1D management platform.

## What it does

Stores and retrieves insulin treatment events: bolus injections, basal rate changes, carbohydrate entries, correction boluses, temp basals, exercise sessions, pump suspends, site changes, sensor inserts, and insulin reservoir changes. Data format follows Nightscout conventions.

## Local development

```bash
# Start service + Keycloak + PostgreSQL
docker compose up --build

# Run tests
./gradlew test

# Run all checks (tests + detekt + kover)
./gradlew check
```

## URLs

| Resource | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger |
| Keycloak Admin | http://localhost:8081 |

## Key environment variables

| Variable | Default | Description |
|---|---|---|
| `JWT_JWKS_URI` | Keycloak well-known URL | JWKS endpoint for JWT validation |
| `JWT_AUDIENCE` | `treatment` | Expected audience claim |
| `DB_URL` | `jdbc:postgresql://localhost:5432/kdiab-treatments` | Database connection URL |
| `DB_USER` | `kdiab_treatments_user` | Database user |
| `DB_PASSWORD` | _(required)_ | Database password |

## API spec

See [`api/openapi.yaml`](api/openapi.yaml) for the full OpenAPI 3.1 specification.

## Database migrations

Managed by Liquibase. Migration files are in `src/main/resources/db/changelog/`.
In production, migrations run in a separate `liquibase-treatments` Docker container.
