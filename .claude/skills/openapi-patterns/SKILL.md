---
name: openapi-patterns
description: OpenAPI 3.1 spec-first API design, naming conventions, and tooling. Apply when designing or modifying any REST API.
when_to_use: Apply when creating or modifying API endpoints, writing OpenAPI specs, or reviewing API contracts.
user-invocable: false
paths: "**/openapi.yaml,**/openapi.yml,**/*Controller.kt,**/*controller.ts,**/*Endpoints.cs"
---

Apply these patterns when designing APIs. See `reference.md` for detailed spec examples.

## Spec-first

Write OpenAPI spec **before** implementation. Generate server stubs and client SDKs from it. The spec is the contract.

## Naming

- Paths: kebab-case plural nouns — `/user-profiles`, `/order-items`
- Operation IDs: camelCase verb+noun — `createUser`, `getUserById`
- Schemas: PascalCase — `User`, `CreateUserRequest`, `PagedResponse`

## HTTP status codes

`200` GET/PUT/PATCH success · `201` POST create (+ `Location` header) · `204` DELETE · `400` validation · `401` unauth · `403` forbidden · `404` not found · `409` conflict · `422` business rule violation · `429` rate limited · `500` unexpected

## Versioning

Path versioning only: `/api/v1/`. Never version individual endpoints.

## Mandatory per operation

`operationId`, `summary`, `tags`, all response codes (including errors), `security` declaration.

## Linting + breaking change detection

```bash
spectral lint openapi/openapi.yaml
oasdiff breaking main:openapi/openapi.yaml openapi/openapi.yaml
```

Both run in CI on every PR touching openapi files.

For detailed patterns and standard schemas → `reference.md`
