# Contributing to kdiab

## Development Environment

**Prerequisites:** JDK 21, Node.js LTS + npm, Docker or Podman with Compose plugin.

```bash
git clone <repo>
cd kdiab
cp .env.example .env          # fill in secrets
docker compose up --build     # full platform
```

Per-service development (faster feedback loop):

```bash
cd kdiab-<service>
docker compose up --build     # service + its own Keycloak + Postgres
./gradlew :backend:run        # or run backend directly
cd kdiab-ui && npm run dev    # frontend dev server with HMR
```

## Issue Tracking

This project uses **beads** (`bd`) for issue tracking:

```bash
bd ready                        # find available work
bd show <id>                    # review issue details
bd update <id> --claim          # claim before starting
bd close <id>                   # close when done
```

Create an issue before writing code. Close it when the work is merged.

## Branching Strategy

- `main` is always releasable
- Feature work in short-lived branches: `feat/<id>-short-description`
- Bug fixes: `fix/<id>-short-description`
- One issue = one branch = one PR

## Commit Messages

All commits must follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add pagination to measure list

fix: correct mmol/L conversion in timeline chart

refactor: extract ProfileService duplicate logic

chore: bump TypeScript to 5.8.3

docs: add Keycloak setup guide
```

Common types: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `ci`.

## Quality Gate

Run the full gate before opening a PR:

```bash
./gradlew check                # all backends: tests + Detekt + Kover (80% coverage)
cd kdiab-ui && npm run build   # frontend: TypeScript + Vite build
```

Per-service backend gate:

```bash
cd kdiab-<service>
./gradlew check       # test + integrationTest + e2eTest + detekt + kover
```

The gate must be **green** before merging. Do not skip `--no-verify` or bypass coverage checks.

## Architecture Conventions

Follow the hexagonal layer boundaries in every service:

```
domain/model/        — pure domain model, no framework imports
domain/repository/   — port interfaces only
application/service/ — business logic, calls repository ports
adapters/inbound/    — HTTP route handlers and mappers
infrastructure/      — persistence adapters (Exposed ORM)
```

- Use `kotlin.uuid.Uuid` and `kotlinx.datetime.*` in domain code; avoid `java.util.UUID` / `java.time.*` in `domain/` or `application/`.
- Throw domain exceptions (`AuthorizationException`, `ResourceNotFoundException`, etc.) instead of setting HTTP status codes manually.
- Update `api/openapi.yaml` first when changing the API; regenerate code on both sides.

## Frontend Conventions

- Feature-based structure under `src/features/`
- Run `npm run api:generate` after any `openapi.yaml` change
- CSS: use custom properties (`var(--text-primary)`) — no hardcoded colours
- No `any` types; use proper type guards instead of `as T` casts

## Code Style

- Detekt enforces Kotlin style; config at `config/detekt/detekt.yml`
- ESLint enforces TypeScript style; run `npm run lint`
- No unnecessary comments — prefer self-documenting names
- No backwards-compatibility shims for removed code
