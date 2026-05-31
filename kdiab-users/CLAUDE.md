# kdiab-users — Agent Context

Port **8088**. User management: settings, doctor-patient relationships. Self-registration is handled natively by Keycloak (#1272).
See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.users`

## Package Structure

```
adapters/inbound/web/
  UserRoutes.kt            # My profile + settings endpoints
  DoctorPatientRoutes.kt   # Doctor-patient relationship management
  UserMapper.kt            # API ↔ domain mappers
application/service/
  UserService.kt           # Settings update with alarm validation
  DoctorPatientService.kt
domain/model/
  User.kt
  UserSettings.kt          # Glucose unit, timezone, alarm thresholds
  DoctorPatientRelation.kt
domain/repository/
  UserSettingsRepository.kt
  DoctorPatientRepository.kt
  IdentityProviderPort.kt  # Port for Keycloak Admin API operations
infrastructure/keycloak/
  KeycloakAdminClient.kt
  KeycloakIdentityProviderAdapter.kt   # Implements IdentityProviderPort
infrastructure/persistence/
  ExposedUserSettingsRepository.kt
  ExposedDoctorPatientRepository.kt
  DatabaseFactory.kt
```

## Domain Rules

- Alarm thresholds must satisfy `urgentHigh > high > low > urgentLow`. All four must be provided or all skipped.
- `urgentLow ≥ 40 mg/dL`; `urgentHigh ≤ 400 mg/dL`.
- Validation in `UserService.validateAlarmThresholds()` — throws `BusinessValidationException` → 400.
- Doctor-patient relationships control which patients a doctor can access via `UserPrincipal.canAccess()`.
- Keycloak Admin API calls are made via `IdentityProviderPort` (abstraction over `KeycloakAdminClient`).

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `users` | Expected `aud` claim |
| `KEYCLOAK_ADMIN_URL` | — | Keycloak base URL for Admin API |
| `KEYCLOAK_REALM` | `kdiab` | Realm name |
| `KEYCLOAK_ADMIN_CLIENT_ID` | `kdiab-users-service` | M2M client ID |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | — | M2M client secret (rotate post-deploy) |
| `JDBC_URL` | — | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | — | DB credentials |
| `PORT` | `8088` | HTTP listen port |
| `APP_INIT_DATABASE` | `true` | Set `false` to skip DB on startup |
