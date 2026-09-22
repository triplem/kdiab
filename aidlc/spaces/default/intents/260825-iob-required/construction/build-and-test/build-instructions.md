# Build Instructions — #1563 activeIob required

Consumes `../iob-required/code-generation/code-generation-plan.md`,
`../iob-required/code-generation/code-summary.md`.

## Backend (kdiab-calc)

kdiab-calc is a composite `includeBuild` module — run Gradle from the module directory, not the root:

```bash
cd kdiab-calc
./gradlew check          # compile + unit + integration + e2e + Detekt + Kover (80% floor)
# or individually:
./gradlew detektMain     # lint only
./gradlew test integrationTest e2eTest
```

`openApiGenerate` runs automatically before `compileKotlin` and regenerates server stubs from the edited
`api/openapi.yaml`. The runtime request body is the hand-written `DoseRequestDto` (not a generated model),
so the OpenAPI edit is contract/doc truth; no generated Kotlin needs manual review.

## Frontend (kdiab-ui)

```bash
cd kdiab-ui
npm run build            # api:generate (measures/profiles/treatments/analyze) + tsc -b + vite
npm run lint             # eslint
npm run test             # vitest
```

There is **no generated calc client**; `src/api/calcApi.ts` is hand-maintained, so `api:generate` does
not touch the calc contract. The build must pass TypeScript strict mode (`tsc -b`) — the tightened
required `activeIob` field is verified there.
