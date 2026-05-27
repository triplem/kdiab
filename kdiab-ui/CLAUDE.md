# kdiab-ui — Agent Context

Port **3005** (root compose) / **3000** (standalone). Unified React SPA covering all nine backend services.
See root `CLAUDE.md` for shared conventions, API URLs, and proxy config.

## Stack

- React 19 + TypeScript (strict)
- Vite + Vitest
- `@tanstack/react-query` — server state
- `react-hook-form` + `zod` — form validation
- `react-oidc-context` — OIDC auth
- `recharts` — charting
- `i18next` + `react-i18next` — i18n (en + de)
- Playwright — E2E tests

## Feature Structure

```
src/features/
  analytics/    # AGP chart, HbA1c, TIR breakdown
  calc/         # Dose calculator (DoseCalculator.tsx — disclaimer required on every result)
  carbs/        # Food entry CRUD
  dashboard/    # CGM trend chart, IOB/COB summary
  measures/     # CGM/BGM/BP/weight/pulse list and entry
  profiles/     # Basal profile view and activation
  timeframe/    # Date-range selector (shared across features)
  timeline/     # Combined timeline view
  treatments/   # Bolus/basal/correction entry and list
  users/        # Settings form (alarm thresholds, glucose unit, timezone)
```

## Auth

Roles parsed directly from the JWT access token (not from OIDC `profile`). Keycloak's OIDC
profile does not reliably carry `realm_access.roles` in all client configurations.

```typescript
// Roles are in the access token under the "roles" claim
const roles = parseRolesFromAccessToken(auth.user?.access_token)
```

## API Client Generation

Generated TypeScript/Axios clients live in `src/api/generated/`. Never edit them by hand.

```bash
npm run api:generate   # regenerates from all openapi.yaml specs
```

Runs automatically before `npm run build`.

## Coverage

Thresholds in `vite.config.ts` (currently below the 80% quality gate — tracked in issue #1082).
Excluded files include generated API clients, thin axios wrappers, and recharts-heavy rendering
components that are only testable via E2E. See `docs/adr/ADR-015-coverage-exclusions.adoc`.

## Clinical Safety Note

`DoseCalculator.tsx` must always render the `doseCalc.disclaimer` i18n key when a result is
present — the dose is a recommendation, not a prescription. Never remove or gate this disclaimer.

## Key File Locations

| File | Purpose |
|---|---|
| `src/App.tsx` | Root: tab routing, auth rendering, role guards, toast handler |
| `src/index.css` | Global styles including `.tab-nav` (min-height: 44px touch targets) |
| `src/i18n/locales/en.json` | English strings |
| `src/i18n/locales/de.json` | German strings |
| `nginx.conf` | Production nginx: CSP, rate limiting, error pages, proxy |
| `Dockerfile` | Multi-stage: Vite build → nginx |
| `vite.config.ts` | Dev server proxy config, test config, coverage config |
