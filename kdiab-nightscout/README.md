# kdiab-nightscout

Nightscout API v3 compatibility layer for the kdiab T1D management platform.

## What it does

Exposes the Nightscout API v3 interface so that diabetes management apps (AAPS, xDrip+, Juggluco) can upload and download data using the Nightscout protocol. Translates between the Nightscout data format and kdiab's internal domain model, storing data in the appropriate kdiab backend services.

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
| Nightscout API | http://localhost:8080/api/v3 |
| Swagger UI | http://localhost:8080/swagger |
| Keycloak Admin | http://localhost:8081 |

## Key environment variables

| Variable | Default | Description |
|---|---|---|
| `JWT_JWKS_URI` | Keycloak well-known URL | JWKS endpoint for JWT validation |
| `JWT_AUDIENCE` | `nightscout` | Expected JWT audience |
| `MEASURES_URL` | `http://localhost:8080` | kdiab-measures base URL |
| `TREATMENTS_URL` | `http://localhost:8083` | kdiab-treatments base URL |

## API spec

See [`api/openapi.yaml`](api/openapi.yaml) for the full OpenAPI 3.1 specification.

## Compatible clients

- AAPS (Android APS)
- xDrip+
- Juggluco
- Any app supporting Nightscout API v3
