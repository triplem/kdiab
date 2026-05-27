# kdiab-treatments — Agent Context

Port **8083**. Treatment event store. See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.treatments`

## Package Structure

```
adapters/inbound/web/
  TreatmentRoutes.kt       # Route handlers — uses generated Paths for type-safe routing
  TreatmentMapper.kt       # Extension functions: API models ↔ domain models
application/service/
  TreatmentService.kt
domain/model/
  Treatment.kt             # Treatment entity + TreatmentType/TreatmentStatus enums
domain/repository/
  TreatmentRepository.kt
infrastructure/persistence/
  ExposedTreatmentRepository.kt
  DatabaseFactory.kt
```

## Domain Model (DB Schema)

```
treatments table:
  id, user_id, treated_at, created_at
  type    (BOLUS | BASAL | CARBS | CORRECTION_BOLUS | COMBO_BOLUS | TEMP_BASAL |
           EXERCISE | NOTE | BG_CHECK | PUMP_SUSPEND | SITE_CHANGE | SENSOR_INSERT | INSULIN_CHANGE)
  data    (JSONB — structure varies by type, follows Nightscout conventions)
  status  (ACTIVE | ARCHIVED)
  notes   (TEXT, nullable)
```

`TreatmentPayload`: the `data` field is mapped to `kotlinx.serialization.json.JsonObject` via `schemaMappings` in `build.gradle.kts`.

## Key Behaviours

- **Delete** is restricted to DOCTOR and ADMIN roles. Patients cannot delete treatments.
- **Archive** (`POST /users/{userId}/treatments/{id}/archive`) sets `status → ARCHIVED`; available to all authorised users.
- Soft delete is the default pattern for patients; hard delete is only available for clinical corrections by DOCTOR/ADMIN.
- EU MDR: 7-year audit log retention. Never purge rows from the `treatments` table.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `treatment` | Expected `aud` claim |
| `JDBC_URL` | — | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | — | DB credentials |
| `PORT` | `8080` | HTTP listen port (remapped to 8083 externally) |
| `APP_INIT_DATABASE` | `true` | Set `false` to skip DB on startup |
