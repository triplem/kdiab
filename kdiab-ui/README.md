# kdiab-ui

Unified SPA frontend for the kdiab T1D management platform.

## What it does

A single React application that provides the user interface for all kdiab backend services:
CGM/BGM measurement history, insulin treatment logging, basal profile management, analytics
(HbA1c, AGP, time-in-range), food/carbohydrate tracking, and the dose calculator.

Authentication is handled via OIDC (Keycloak). Role-based views are parsed directly from
the JWT access token.

## Tech stack

- **React 19** + **TypeScript** (strict mode)
- **Vite** — build tooling and dev server
- **TanStack Query** — server state management
- **react-hook-form** + **zod** — form validation
- **react-oidc-context** — OIDC/Keycloak authentication
- **Recharts** — charting (AGP, CGM timeline)
- **Axios** — HTTP client with correlation-ID interceptor

## Local development

```bash
npm install
npm run dev          # dev server at http://localhost:3005
npm run build        # generate API clients + TypeScript compile + Vite build
npm run lint         # ESLint
npm run test         # Vitest unit tests
npm run test:e2e     # Playwright e2e tests (requires a running stack)
```

To regenerate TypeScript API clients from the backend OpenAPI specs:

```bash
npm run api:generate
```

## Connecting to backends

The dev server proxies API requests to the backend services. The full stack (all services +
Keycloak + PostgreSQL) can be started from the monorepo root:

```bash
# From repo root
docker compose up --build
```

Frontend is then available at **http://localhost:3005**.
