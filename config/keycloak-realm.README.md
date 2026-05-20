# config/keycloak-realm.json

Single source of truth for the Keycloak realm used by the root `docker-compose.yml`.

## Why one file

Keycloak's `--import-realm` flag imports one complete realm per JSON file. There is no
built-in mechanism to merge two partial realm files at startup. Splitting the file into
`keycloak-realm-base.json` + `keycloak-realm-testdata.json` would therefore require a
custom startup script or post-boot Admin API calls — adding operational complexity for no
runtime benefit. The boundary is documented here instead.

## Production config (everything except `users`)

The following sections belong to all environments and must be kept in sync with any
production Keycloak deployment:

| Section | Contents |
|---|---|
| `realm`, `enabled`, `sslRequired` | Realm identity and TLS policy |
| `registrationAllowed`, `loginTheme` | Self-service and UI settings |
| `internationalizationEnabled`, `supportedLocales`, `defaultLocale` | i18n |
| `roles.realm` | `PATIENT`, `DOCTOR`, `ADMIN` |
| `requiredActions` | `VERIFY_PROFILE` (enforced on all users) |
| `clients` | All frontend and service clients with their protocol mappers and audience mappings |

### Clients

| Client ID | Type | Purpose |
|---|---|---|
| `kdiab-measures-frontend` | public, PKCE | Per-service dev compose (measures) |
| `kdiab-profiles-frontend` | public, PKCE | Per-service dev compose (profiles) |
| `kdiab-treatments-frontend` | public, PKCE | Per-service dev compose (treatments) |
| `kdiab-ui` | public, PKCE | Unified SPA (root compose, port 3005) |
| `kdiab-analyze-frontend` | public, PKCE | BFF client; carries all six JWT audiences |
| `kdiab-users-service` | confidential, service account | Keycloak Admin API access for user management |

### JWT audiences per client

The `kdiab-ui` and `kdiab-analyze-frontend` clients carry audience mappers for all
upstream services so a single token is accepted everywhere:
`analyze`, `measure`, `profile`, `treatment`, `carbs`, `calc`, `users`.

## Test data (the `users` array)

The `users` array contains pre-seeded accounts for local development and integration
tests. **These users must never be created in a production Keycloak instance.**

| Username | Role | Password | Notes |
|---|---|---|---|
| `sarah` | PATIENT | `password` | glucose_unit=mg/dL; seed data in `config/postgres/02-seed-data.sql` |
| `mike` | PATIENT | `password` | glucose_unit=mmol/L |
| `dr_house` | DOCTOR | `password` | allowed_patients=[sarah's UUID] |
| `dr_cameron` | DOCTOR | `password` | allowed_patients=[mike's UUID] |
| `admin` | ADMIN | `password` | |
| `service-account-kdiab-users-service` | — | — | Service account; auto-created by Keycloak |

To deploy to production: remove the `"users"` array from the realm JSON before importing,
or use the Keycloak Admin UI to create real users separately.

## M2M client secret rotation (required in production)

The `kdiab-users-service` confidential client is imported with the placeholder secret
`change-me-in-production`. Keycloak's `--import-realm` processes realm JSON as raw JSON
and does **not** substitute environment variable expressions in the `secret` field.

**After the first deploy**, rotate the secret via the Keycloak Admin API or Admin Console:

```bash
# Via Admin API (replace HOST, REALM, and CLIENT_ID as needed)
KC_ADMIN_TOKEN=$(curl -s -X POST \
  https://HOST/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli&grant_type=password&username=admin&password=ADMIN_PW" \
  | jq -r .access_token)

CLIENT_UUID=$(curl -s -H "Authorization: Bearer $KC_ADMIN_TOKEN" \
  https://HOST/admin/realms/kdiab/clients?clientId=kdiab-users-service \
  | jq -r '.[0].id')

curl -s -X POST \
  "https://HOST/admin/realms/kdiab/clients/$CLIENT_UUID/client-secret" \
  -H "Authorization: Bearer $KC_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"type\":\"secret\",\"value\":\"$(openssl rand -base64 32)\"}"
```

Store the generated secret in your secrets manager and inject it into the kdiab-users
service via `KC_CLIENT_SECRET` (or equivalent env var) — never commit it to source control.

## Keycloak theme

The custom login theme lives at `config/keycloak-theme/` (mounted read-only into the
Keycloak container as `/opt/keycloak/themes/kdiab`). It is activated via
`"loginTheme": "kdiab"` in the realm settings above.
