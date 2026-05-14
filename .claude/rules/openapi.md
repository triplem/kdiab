# Rule: OpenAPI

All service APIs must have an OpenAPI 3.1 specification. See `/openapi-patterns` for implementation details.

## Mandatory Fields

Every spec must include:

```yaml
openapi: "3.1.0"
info:
  title: "Service Name API"
  version: "1.0.0"
  description: "What this service does"
  contact:
    name: "Team Name"
    email: "team@example.com"
servers:
  - url: "https://api.example.com/api/v1"
    description: Production
tags:
  - name: Users
    description: User management operations
```

## Schema Requirements

- All schemas must have `required` arrays for mandatory fields.
- All properties must have `description`.
- All string fields must have `example`.
- Numeric fields must specify `format` (`int32`, `int64`, `float`, `double`).
- Date/time fields must use `format: date-time` (ISO 8601).

## Operation Requirements

Every operation must have:
- `operationId` (camelCase, unique)
- `summary` (one line)
- `tags` (at least one)
- `responses` (all expected status codes, including error responses)
- `security` (explicit, even if `[]`)

## CI Enforcement

```yaml
# .github/workflows/api-check.yml
- name: Lint OpenAPI spec
  run: spectral lint openapi/openapi.yaml --ruleset .spectral.yaml

- name: Check breaking changes
  if: github.event_name == 'pull_request'
  run: oasdiff breaking origin/main:openapi/openapi.yaml openapi/openapi.yaml
```

## SDK Generation

Run code generation as part of the build, not as a manual step. Generated code is committed to source control so diffs are visible in PRs.
