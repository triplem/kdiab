# kdiab-measures — Agent Context

Port **8080**. Primary health measurement store. See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.measures`

## Package Structure

```
adapters/inbound/web/
  MeasureRoutes.kt         # Route handlers — uses generated Paths for type-safe routing
  MeasureMapper.kt         # Extension functions: API models ↔ domain models
application/service/
  MeasureService.kt
domain/model/
  Measure.kt               # Measure entity + MeasureType/Source/Status enums
domain/repository/
  MeasureRepository.kt
infrastructure/persistence/
  ExposedMeasureRepository.kt
  DatabaseFactory.kt
```

## Data Flow

```
HTTP Request
  → MeasureRoutes (authenticate, checkReadAccess/checkWriteAccess)
  → MeasureMapper.toDomain()
  → MeasureService (business logic, throws domain exceptions)
  → MeasureRepository → ExposedMeasureRepository (suspendTransaction on Dispatchers.IO)
HTTP Response ← MeasureMapper.toApi() ← StatusPages
```

## Domain Model (DB Schema)

```
measures table:
  id, user_id, measured_at, created_at
  type    (CGM | BGM | BLOOD_PRESSURE | WEIGHT | PULSE)
  source  (MANUAL | NIGHTSCOUT | GOOGLE_FIT | APPLE_HEALTH)
  data    (JSONB — structure varies by type, e.g. {"sgv": 120, "trend": "Flat"})
  status  (ACTIVE | ARCHIVED)
```

`MeasurePayload`: the `data` field is mapped to `kotlinx.serialization.json.JsonObject` via `schemaMappings` in `build.gradle.kts` so the generator produces properly serializable code.

## Key Behaviours

- **Delete** (`DELETE /users/{userId}/measures`) is a **hard physical delete**, restricted to DOCTOR and ADMIN. Records are permanently removed.
- **Archive** (`POST /users/{userId}/measures/archive`) accepts `{"measureIds": [...]}` and sets `status → ARCHIVED`. Returns 200 OK. This is the soft-delete path for patients.
- CGM bulk inserts via kdiab-nightscout are limited to 100 readings per request.
- EU MDR: never purge rows from the `measures` table; archived records satisfy retention requirements.
- Highest-data-volume service: CGM devices write one record every 5 minutes per patient.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `measure` | Expected `aud` claim |
| `JDBC_URL` | — | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | — | DB credentials |
| `PORT` | `8080` | HTTP listen port |
| `DB_POOL_SIZE` | `10` | HikariCP max connections |
| `APP_INIT_DATABASE` | `true` | Set `false` to skip DB on startup |
