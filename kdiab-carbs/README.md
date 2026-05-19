# kdiab-carbs

Food and carbohydrate database service for the kdiab T1D management platform.

## What it does

Maintains a searchable food database with carbohydrate content per serving. Supports user-created custom food entries. Used by the dose calculator (kdiab-calc) to look up carbohydrate content when computing bolus recommendations.

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
| `JWT_AUDIENCE` | `carbs` | Expected audience claim |
| `DB_URL` | `jdbc:postgresql://localhost:5432/kdiab-carbs` | Database connection URL |
| `DB_USER` | `kdiab_carbs_user` | Database user |
| `DB_PASSWORD` | _(required)_ | Database password |

## API spec

See [`api/openapi.yaml`](api/openapi.yaml) for the full OpenAPI 3.1 specification.

## Database migrations

Managed by Liquibase. Migration files are in `src/main/resources/db/changelog/`.
In production, migrations run in a separate `liquibase-carbs` Docker container.
