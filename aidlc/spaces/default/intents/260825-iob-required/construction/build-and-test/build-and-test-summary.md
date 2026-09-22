# Build & Test Summary — #1563 activeIob required (kdiab-calc)

Consumes `../iob-required/code-generation/code-generation-plan.md`,
`../iob-required/code-generation/code-summary.md`.

## Nature of "build and test" for this change

A focused, compilable Kotlin change in **kdiab-calc** plus a TypeScript type-tighten + i18n change in
**kdiab-ui**. Both are buildable, so the full quality gate runs: `./gradlew check` (compile + unit +
integration + e2e + Detekt + Kover) for the backend, and `npm run build` + `npm run lint` +
`npm run test` for the frontend. No other module changed (kdiab-common untouched), so only kdiab-calc +
kdiab-ui were built.

## What was verified (locally, this stage)

| Gate | Command | Result |
|---|---|---|
| Backend compile + all tests + Detekt + Kover 80% | `cd kdiab-calc && ./gradlew check` | ✅ BUILD SUCCESSFUL |
| Frontend TS strict build (tsc -b + vite) | `cd kdiab-ui && npm run build` | ✅ built |
| Frontend lint | `npm run lint` (eslint) | ✅ clean |
| Frontend unit/component tests | `npm run test` (vitest) | ✅ 684/684 across 48 files |

One Detekt `MaxLineLength` violation on the new warning string was found and fixed (wrapped the string)
before the gate passed — logged in `build-test-results.md`.

## Test strategy (Minimal — bugfix scope)

Requirement-driven tests for FR-2 (reject omitted/null/negative IOB → 400) and FR-3 (zero-IOB warning
gated on a recommended correction), plus regression coverage that the dose math (NFR-2) is unchanged.
See `unit-test-instructions.md`, `integration-test-instructions.md`, `performance-test-instructions.md`,
`security-test-instructions.md`.

## Verdict

**PASS.** The whole three-tier backend suite and the frontend suite are green; coverage floor met. The
change is ready for delivery. See `build-test-results.md` for the evidence.
