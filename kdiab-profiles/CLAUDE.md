# kdiab-profiles — Agent Context

Port **8082**. Insulin pump basal profile management. See root `CLAUDE.md` for shared conventions.

Root package: `org.javafreedom.kdiab.profiles`

## Package Structure

```
adapters/inbound/web/   # ProfileRoutes, InsulinRoutes, ProfileMapper
application/service/    # ProfileService — business logic, owns state machine transitions
domain/model/           # Profile, Insulin, segment types, ProfileStatus enum
domain/repository/      # ProfileRepository, InsulinRepository
infrastructure/persistence/  # ExposedProfileRepository, etc.
```

## Profile State Machine

```
DRAFT ──(activate)──► ACTIVE ──(update or new activation)──► ARCHIVED + new ACTIVE
                                                                        ↑
PROPOSED ──(patient accepts)──────────────────────────────────────────ACTIVE
         ──(patient rejects)──► ARCHIVED
```

- Active profiles are **immutable** — any update archives the current and creates a new ACTIVE version linked via `previousProfileId`.
- Clients receive a **new profile ID** on every update — local state must update its reference.
- PROPOSED status enables doctor-patient collaboration: a doctor proposes, the patient accepts or rejects.

## Domain Validation

Clinical safety checks live in `Profile.validate()` in the domain model:
- Max daily basal ≤ 150 U/day
- ICR / ISF range checks
- Unit heuristics

## Database Note

The `IDX_PROFILES_USER_ACTIVE` partial index (`WHERE status = 'ACTIVE'`) enforcing one active profile per user is defined with `dbms: postgresql` — **not created in H2**. Integration tests use H2 in-memory; the unique constraint is only enforced in real PostgreSQL deployments.

## Key ADRs

- ADR-015 — Copy-on-Write profiles
- ADR-016 — Doctor-Patient collaboration via PROPOSED status
- ADR-302 — No Users table (userId from JWT `sub`)
- ADR-303 — JWT/RBAC via Keycloak `realm_access.roles`

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `JWKS_URL` | — | Keycloak JWKS endpoint |
| `JWT_DOMAIN` | — | JWT issuer |
| `JWT_AUDIENCE` | `profile` | Expected `aud` claim |
| `JDBC_URL` | — | PostgreSQL JDBC URL |
| `DB_USER` / `DB_PASSWORD` | — | DB credentials |
| `PORT` | `8080` | HTTP listen port (remapped to 8082 externally) |
| `APP_INIT_DATABASE` | `true` | Set `false` to skip DB on startup |
