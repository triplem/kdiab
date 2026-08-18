# Frontend Components — N/A

## Not applicable for this intent

This is a **backend/build-only** refactor (`requirements.md` #1556, § Out of scope). No frontend or
UI work is involved:

- `kdiab-ui` is untouched — the SPA logs via **Pino** (a separate stack), not Logback, so the
  encoder swap does not reach it.
- No component, route, form, or API-client change; no `.tsx`/`.ts` file is modified.

The `type-check` / `linter` sensors on this stage find no TypeScript/JSX snippets to check (the
design contains only XML, TOML, and Kotlin-DSL fragments).
