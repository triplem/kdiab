# kdiab-carbs — Agent Context

Port **8085**. Food / carbohydrate database and entry tracking.
See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.carbs`

## Package Structure

```
adapters/inbound/web/
  FoodEntryRoutes.kt       # CRUD + archive endpoints; uses generated Paths
  FoodEntryMapper.kt       # API models ↔ domain models
application/service/
  FoodEntryService.kt
domain/model/
  FoodEntry.kt             # FoodEntry + FoodEntryStatus (ACTIVE | ARCHIVED) + PagedFoodEntries
domain/repository/
  FoodEntryRepository.kt
infrastructure/persistence/
  ExposedFoodEntryRepository.kt
  DatabaseFactory.kt
```

## Domain Model

```kotlin
data class FoodEntry(
    val id: Uuid,
    val userId: Uuid,
    val name: String,
    val portionGrams: Double,
    val carbsPer100g: Double,
    val status: FoodEntryStatus,   // ACTIVE | ARCHIVED
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val carbsForPortion: Double get() = portionGrams * carbsPer100g / 100.0
}
```

## Access Control

- **PATIENT** — CRUD own entries only. Cannot access another patient's entries.
- **DOCTOR** — read-only access to allowed patients' entries (via `allowedPatients` JWT claim). Cannot create, update, delete, or archive.
- **ADMIN** — full access to any user's entries.

## Key Behaviours

- Soft delete via `POST /users/{userId}/foods/{foodId}/archive` → `status = ARCHIVED`.
- Hard delete (`DELETE /users/{userId}/foods/{foodId}`) is available to patients for their own entries.
- `carbsForPortion` is a computed property — not stored in the DB.
- Entries are paginated; default page size 20, max 100.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `carbs` | Expected `aud` claim |
| `JDBC_URL` | — | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | — | DB credentials |
| `PORT` | `8080` | HTTP listen port (remapped to 8085 externally) |
| `APP_INIT_DATABASE` | `true` | Set `false` to skip DB on startup |
