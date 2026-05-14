# OpenAPI Patterns Reference

## Standard schemas

```yaml
# components/schemas/ErrorResponse.yaml
ErrorResponse:
  type: object
  required: [code, message]
  properties:
    code: { type: string, example: USER_NOT_FOUND }
    message: { type: string }
    details:
      type: array
      items:
        type: object
        properties:
          field: { type: string }
          message: { type: string }

# components/schemas/PagedResponse.yaml
PagedResponse:
  type: object
  required: [content, page, totalElements, totalPages]
  properties:
    content: { type: array, items: {} }
    page: { type: integer }
    size: { type: integer }
    totalElements: { type: integer, format: int64 }
    totalPages: { type: integer }
```

## Operation template

```yaml
/users/{userId}:
  get:
    operationId: getUserById
    summary: Get a user by ID
    tags: [Users]
    security:
      - BearerAuth: []
    parameters:
      - name: userId
        in: path
        required: true
        schema: { type: integer, format: int64 }
        description: The user's unique identifier
    responses:
      '200':
        description: User found
        content:
          application/json:
            schema: { $ref: '#/components/schemas/UserResponse' }
      '404':
        description: User not found
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ErrorResponse' }
```

## Spectral config

```yaml
# .spectral.yaml
extends: ["spectral:oas"]
rules:
  operation-operationId: error
  operation-tags: error
  info-contact: warn
```
