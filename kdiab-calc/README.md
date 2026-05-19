# kdiab-calc

Stateless dose calculator service for the kdiab T1D management platform.

## What it does

Computes bolus dose recommendations from the user's active insulin pump profile, current CGM glucose trend, and planned carbohydrate intake. Applies the insulin-on-board (IOB) and carb-on-board (COB) algorithms. Has no database — purely a calculation service.

## Local development

```bash
# Start service + Keycloak
docker compose up --build

# Run tests
./gradlew test

# Run all checks (tests + detekt + kover)
./gradlew check
```

## URLs

| Resource | URL |
|---|---|
| API | http://localhost:8086/api/v1 |
| Swagger UI | http://localhost:8086/swagger |
| Keycloak Admin | http://localhost:8081 |

## Key environment variables

| Variable | Default | Description |
|---|---|---|
| `JWT_AUDIENCE` | `calc` | Expected JWT audience |
| `PROFILES_URL` | `http://localhost:8082` | kdiab-profiles base URL for active profile |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments base URL for IOB calculation |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3003` | Allowed CORS origins |

## API spec

See [`api/openapi.yaml`](api/openapi.yaml) for the full OpenAPI 3.1 specification.
