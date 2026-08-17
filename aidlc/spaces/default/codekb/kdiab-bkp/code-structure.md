# Code Structure — kdiab (T1D Management Platform)

## Repository Layout

Single Git repository (`kdiab-bkp`, `main` branch) organised as a **Gradle composite build**.
Each backend service is its own Gradle root wired in via `includeBuild`; the frontend is a
separate npm/Vite project; shared build conventions live in an included `build-logic` build.

```
kdiab-bkp/
├── settings.gradle.kts        # 9 includeBuild + dependencySubstitution (spec -> local project)
├── build.gradle.kts           # aggregate tasks: buildBackends, compileBackends, build,
│                              #   check, clean, dockerBuild, asciidoctor
├── gradle.properties          # parallel + config-cache + caching on; 2 GB heaps
├── gradle/libs.versions.toml  # central version catalog (single source of dependency versions)
├── build-logic/               # included build: 3 convention plugins + UpstreamSpecExtensions.kt
├── config/                    # postgres init/seed, keycloak realm+theme, openapi-templates, detekt shared
├── docs/                      # 23 platform ADRs, security/accepted-risks.md, testing/, asciidoc
├── kdiab-common/              # shared Kotlin library (published to GitHub Packages)
├── kdiab-measures/            # domain service (8080)
├── kdiab-profiles/            # domain service (8082)
├── kdiab-treatments/          # domain service (8083)
├── kdiab-analyze/             # stateless BFF (8084)
├── kdiab-carbs/               # domain service (8085)
├── kdiab-calc/                # stateless calculator (8086)
├── kdiab-nightscout/          # stateless Nightscout facade (8087)
├── kdiab-users/               # domain service (8088)
└── kdiab-ui/                  # React 19 SPA (npm/Vite, feature-based)
```

## Uniform Hexagonal Package Layout (backend services)

Every backend uses the package root `org.javafreedom.kdiab.<svc>` and the same hexagonal
package shape. This uniformity is a deliberate, load-bearing convention — a developer moving
between services finds the same map.

```
adapters/inbound/web/          # Ktor route handlers + mappers (API <-> domain)
adapters/outbound/http/        # Ktor HTTP clients to upstreams  (analyze, calc, nightscout only)
application/service/           # business logic
application/port/outbound/     # outbound ports                  (analyze only)
domain/model/                  # pure domain model (no framework deps)
domain/repository/             # repository port interfaces       (absent in stateless services)
domain/exception/              # domain exceptions
infrastructure/persistence/    # Exposed ORM + PostgreSQL adapter (absent in stateless services)
infrastructure/keycloak/       # Keycloak Admin API adapter        (users only)
plugins/                       # Ktor plugin configuration
```

**Presence-by-type rules:**

- **Domain services** (measures, profiles, treatments, carbs, users) have
  `domain/repository`, `infrastructure/persistence`, but no `adapters/outbound/http`.
- **Composition services** (analyze, calc, nightscout) have `adapters/outbound/http` and no
  persistence layer; **analyze** additionally has `application/port/outbound` (outbound ports).
- **kdiab-users** uniquely adds `infrastructure/keycloak` for its Keycloak Admin API integration.

## File Classification and Size Signals

| Module | Main `.kt` files | Type | Notes |
|---|---|---|---|
| kdiab-common | 23 | shared library | Domain types + 18 shared Ktor plugins + UserPrincipal |
| kdiab-users | 33 | domain service | Largest — settings, relations, invitations, API keys, Keycloak |
| kdiab-analyze | 21 | stateless BFF | Outbound ports + HTTP clients |
| kdiab-nightscout | 16 | stateless facade | Fans out to 5 upstreams |
| kdiab-profiles | 16 | domain service | Copy-on-write, ACTIVE/ARCHIVED state |
| kdiab-treatments | 15 | domain service | Events + device status |
| kdiab-calc | 9 | stateless calc | Single endpoint |
| kdiab-measures | 8 | domain service | Primary time-series store |
| kdiab-carbs | 8 | domain service | Food/carb DB + entries |

**Frontend (kdiab-ui):** 115 `.tsx` components, 34 hand-written `.ts`, 20 generated `.ts`
API-client files. Feature-based folders: analytics, calc, carbs, dashboard, measures,
profiles, report, timeframe, timeline, treatments, users.

## Code Patterns and Conventions

- **Route pattern** — one private function per endpoint; type-safe routing via generated
  `Paths` from the OpenAPI spec (kdiab-analyze uses manual routing, no generated Paths).
  Access control checked at route entry via `checkReadAccess` / `checkWriteAccess`.
- **Errors via domain exceptions** — routes throw `DomainExceptions`
  (Authentication/Authorization/ResourceNotFound/BusinessValidation/Conflict) rather than
  emitting HTTP status codes manually; the shared `StatusPages` plugin maps them to HTTP.
- **Domain purity** — domain code uses `kotlin.uuid.Uuid` and `kotlinx.datetime.Instant/LocalTime`;
  `java.time.*` and `java.util.UUID` are avoided in `domain/` and `application/`.
- **No Users table** — `userId` is stored directly as a UUID; identity comes from the Keycloak
  JWT `sub` claim.
- **Mapper layer** — inbound web adapters translate generated API models to/from domain,
  keeping generated code out of the domain.

## Build Convention Plugins (`build-logic`)

Three precompiled Gradle script plugins carry the shared construction rules, plus a helper:

| Plugin / file | Applied by | Provides |
|---|---|---|
| `kdiab.kotlin-base` | all backends | Kotlin/JVM 21 toolchain, Detekt, security-pinned transitive constraints (Jackson, Handlebars) |
| `kdiab.ktor-service` | stateless services + base for db-service | Ktor, OpenAPI codegen, three JvmTestSuites (unit/integration/e2e), Kover 80% floor |
| `kdiab.ktor-db-service` | domain services | adds Exposed/HikariCP/PostgreSQL/Liquibase persistence conventions |
| `UpstreamSpecExtensions.kt` | analyze, calc, nightscout | `registerUpstreamSpec` — declares an upstream service's spec as a build input for typed-client generation |

The convention plugins absorb most per-service boilerplate; residual duplication (per-module
Detekt config/baseline required by the composite build, near-identical `openApiGenerate`/`kover`
blocks, per-service Dockerfiles) remains a known debt signal.

## Generated vs Hand-Written Code

- **Backend generated code** lives under each service's `build/generated/api/` from
  `openApiGenerate` (openapi-generator 7.21.0, kotlin-server/ktor, kotlinx_serialization) and
  runs before `compileKotlin`. Shared mustache templates are at `config/openapi-templates`.
  It is excluded from coverage and Detekt.
- **Frontend generated code** (20 `.ts` files) is produced by `npm run api:generate`
  (typescript-axios) before `npm run build`. Note the gap: only 4 of 8 backend specs are wired
  into `api:generate`, so carbs/calc/nightscout/users features use hand-written Axios clients.
