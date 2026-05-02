# CLAUDE.md — kdiab-measures

This file provides service-specific guidance. Common commands, architecture patterns, and agent personas are in the parent `CLAUDE.md`.

## Project Overview

**kdiab-measures** is the T1D health measurement tracking service. It stores heterogeneous health data (CGM, BGM, blood pressure, weight, pulse) via a flexible JSONB payload model.

## Backend Package Structure
Root package: `org.javafreedom.kdiab.measures`

```
adapters/inbound/web/
  MeasureRoutes.kt         # Route handlers — uses generated Paths for type-safe routing
  MeasureMapper.kt         # Extension functions: API models ↔ domain models
application/service/
  MeasureService.kt
domain/model/
  Measure.kt               # Measure entity + MeasureType/Source/Status enums
  Role.kt
domain/repository/
  MeasureRepository.kt
domain/exception/
  DomainExceptions.kt
infrastructure/persistence/
  ExposedMeasureRepository.kt
  DatabaseFactory.kt
plugins/
  Security.kt, StatusPages.kt, Logging.kt, ErrorResponse.kt
```

## Data Flow
```
HTTP Request
  → MeasureRoutes (authenticate, checkReadAccess/checkWriteAccess)
  → MeasureMapper.toDomain()
  → MeasureService (business logic, throws domain exceptions)
  → MeasureRepository → ExposedMeasureRepository (suspendTransaction on Dispatchers.IO)
HTTP Response
  ← MeasureMapper.toApi()
  ← StatusPages (catches domain exceptions → structured ErrorResponse JSON)
```

## Domain Model
```
measures table:
  id, user_id, measured_at, created_at
  type    (CGM | BGM | BLOOD_PRESSURE | WEIGHT | PULSE)
  source  (MANUAL | NIGHTSCOUT | GOOGLE_FIT | APPLE_HEALTH)
  data    (JSONB — structure varies by type, e.g. {"sgv": 120, "trend": "Flat"})
  status  (ACTIVE | ARCHIVED)
```

**Special case — `MeasurePayload`**: The `data` field is mapped to `kotlinx.serialization.json.JsonObject` via `schemaMappings` in `build.gradle.kts` so the generator produces properly serializable code.

Database migrations managed by Liquibase (runs as a separate service before the backend).
