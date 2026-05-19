# kdiab-users

User settings and doctor-patient relationship service for the kdiab T1D management platform.

## What it does

Stores per-user preferences (timezone, language, time format, glucose unit, weight unit, alarm thresholds, sensor duration). Manages doctor-patient assignments (which doctors can read which patients' data). Identity comes from the Keycloak JWT `sub` claim — there is no separate users table.

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
| `JWT_AUDIENCE` | `users` | Expected audience claim |
| `DB_URL` | `jdbc:postgresql://localhost:5432/kdiab-users` | Database connection URL |
| `DB_USER` | `kdiab_users_user` | Database user |
| `DB_PASSWORD` | _(required)_ | Database password |
| `KEYCLOAK_ADMIN_URL` | `http://localhost:8081` | Keycloak Admin API base URL |
| `KEYCLOAK_REALM` | `kdiab` | Realm name |
| `KEYCLOAK_CLIENT_ID` | _(required)_ | Service account client ID |
| `KEYCLOAK_CLIENT_SECRET` | _(required)_ | Service account client secret |

## API spec

See [`api/openapi.yaml`](api/openapi.yaml) for the full OpenAPI 3.1 specification.

## Database migrations

Managed by Liquibase. Migration files are in `src/main/resources/db/changelog/`.
In production, migrations run in a separate `liquibase-users` Docker container.
