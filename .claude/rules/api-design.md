# Rule: API Design

All REST APIs must follow these rules. See `/openapi-patterns` for OpenAPI spec implementation.

## Spec-First

Write the OpenAPI spec before writing any code. The spec is the source of truth. Generate server stubs and client SDKs from it.

## Resource Naming

- Use nouns, not verbs: `/users` not `/getUsers`
- Plural for collections: `/users`, `/orders`
- Hierarchical for ownership: `/users/{userId}/orders`
- Kebab-case for multi-word resources: `/user-profiles`, `/order-items`
- No file extensions in paths

## HTTP Methods

| Method | Semantics | Idempotent | Safe |
|---|---|---|---|
| GET | Read resource(s) | Yes | Yes |
| POST | Create resource / non-idempotent action | No | No |
| PUT | Replace full resource | Yes | No |
| PATCH | Partial update | No | No |
| DELETE | Remove resource | Yes | No |

## Request / Response Design

- Always return a consistent error body: `{ "code": "DOMAIN_ERROR", "message": "...", "details": [...] }`
- Return the created/updated resource in the response body (don't force a second GET)
- Use `Location` header on 201 responses: `Location: /api/v1/users/42`
- Use `ETag` for cacheable resources

## Pagination

All list endpoints must support pagination:

```
GET /api/v1/users?page=0&size=20&sort=createdAt,desc
```

Response:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8
}
```

Max page size: 100. Default: 20.

## Filtering & Sorting

```
GET /api/v1/users?status=ACTIVE&createdAfter=2026-01-01&sort=name,asc
```

Document all filter parameters in the OpenAPI spec.

## Versioning

Path versioning only: `/api/v1/`, `/api/v2/`.

Never version individual endpoints — version the whole API. Maintain n-1 versions in production (v1 while v2 is current).

## Security

- All endpoints require auth by default (see `security.md`).
- Use `security: []` to explicitly mark public endpoints.
- Rate limiting headers on all endpoints: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`

## Backwards Compatibility

A change is breaking if it:
- Removes a field from a response
- Changes a field type
- Adds a required request field
- Changes the meaning of a status code
- Removes an endpoint

Breaking changes require a major version bump. Non-breaking additions (new optional fields, new endpoints) can be minor bumps.

## Observability

Every endpoint auto-emits:
- Request count (by method, path, status)
- Request duration histogram
- Error rate

Use Micrometer (JVM), Pino + Prometheus client (Node), or ASP.NET Core metrics.
