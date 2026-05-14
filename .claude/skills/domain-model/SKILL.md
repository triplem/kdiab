---
name: domain-model
description: Build or update the project's domain model (aggregates, entities, value objects, events) from the codebase and requirements. Use when starting a new project, after an epic completes, or when domain drift is detected.
disable-model-invocation: true
effort: high
allowed-tools: Read Write Bash(find src/ -name "*.kt" -o -name "*.ts" -o -name "*.cs") Bash(cat docs/requirements.md) Bash(ls db/migration/ 2>/dev/null)
---

## Domain classes in codebase

!`find . -path "*/domain/*.kt" -o -path "*/domain/*.ts" -o -path "*/entities/*.cs" 2>/dev/null | head -30 || echo "Searching for domain classes..."`

## Database schema

!`ls db/migration/ 2>/dev/null | tail -5 || echo "No migration directory found"`

## Instructions

### 1 — Discover

Read:
- `docs/requirements.md` for business entities
- All domain/entity/model classes found above
- Latest DB migration files for current schema
- `openapi/openapi.yaml` for DTO shapes

### 2 — Build model

Identify:
- **Aggregates** — clusters of related entities with a single root
- **Entities** — objects with identity (have an ID)
- **Value Objects** — immutable, identity-less objects
- **Domain Events** — things that happened (past tense)
- **Domain Services** — operations that don't belong to a single entity

### 3 — Write `docs/domain-model.md`

```markdown
# Domain Model

## Aggregates

### <Name> (root: <Entity>)
- <Entity> [entity] — fields
- <Vo> [value object] — fields

## Domain Events

| Event | Published by | Consumed by |
|---|---|---|

## Ubiquitous Language

| Term | Definition |
|---|---|
```

### 4 — Challenge

`/challenge ArchitectAgent "Review domain model for DDD compliance and consistency with codebase"`
Incorporate feedback.

### 5 — Extract learnings

If domain drift is found → `/learn "Domain model drift: <description>"`

## Output

- `docs/domain-model.md`
- Ubiquitous language glossary

Run after: requirements approval, each epic completion, or on `/challenge ArchitectAgent` recommendation.
