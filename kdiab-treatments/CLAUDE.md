# CLAUDE.md — kdiab-treatments

This file provides service-specific guidance. Common commands, architecture patterns, and agent personas are in the parent `CLAUDE.md`.

## Project Overview

**kdiab-treatments** is the T1D treatment event tracking service. It records treatment events (bolus, basal, carbs, corrections, exercise, notes, pump/sensor events) following Nightscout conventions via a flexible JSONB payload model.

## Backend Package Structure
Root package: `org.javafreedom.kdiab.treatments`

```
adapters/inbound/web/
  TreatmentRoutes.kt       # Route handlers — uses generated Paths for type-safe routing
  TreatmentMapper.kt       # Extension functions: API models ↔ domain models
application/service/
  TreatmentService.kt
domain/model/
  Treatment.kt             # Treatment entity + TreatmentType/TreatmentStatus enums
  Role.kt
domain/repository/
  TreatmentRepository.kt
domain/exception/
  DomainExceptions.kt
infrastructure/persistence/
  ExposedTreatmentRepository.kt
  DatabaseFactory.kt
plugins/
  Security.kt, StatusPages.kt, Logging.kt, ErrorResponse.kt
```

## Data Flow
```
HTTP Request
  → TreatmentRoutes (authenticate, checkReadAccess/checkWriteAccess)
  → TreatmentMapper.toDomain()
  → TreatmentService (business logic, throws domain exceptions)
  → TreatmentRepository → ExposedTreatmentRepository (suspendTransaction on Dispatchers.IO)
HTTP Response
  ← TreatmentMapper.toApi()
  ← StatusPages (catches domain exceptions → structured ErrorResponse JSON)
```

## Domain Model
```
treatments table:
  id, user_id, treated_at, created_at
  type    (BOLUS | BASAL | CARBS | CORRECTION_BOLUS | COMBO_BOLUS | TEMP_BASAL |
           EXERCISE | NOTE | BG_CHECK | PUMP_SUSPEND | SITE_CHANGE | SENSOR_INSERT | INSULIN_CHANGE)
  data    (JSONB — structure varies by type, follows Nightscout conventions)
  status  (ACTIVE | ARCHIVED)
  notes   (TEXT, nullable — optional free-text annotation)
```

**Special case — `TreatmentPayload`**: The `data` field is mapped to `kotlinx.serialization.json.JsonObject` via `schemaMappings` in `build.gradle.kts` so the generator produces properly serializable code.

**Route note**: Delete is restricted to DOCTOR and ADMIN roles; archive is available to all authorized users.

Database migrations managed by Liquibase (runs as a separate service before the backend).
