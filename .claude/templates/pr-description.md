# {{TYPE}}({{SCOPE}}): {{STORY_TITLE}} (#{{STORY_ID}})

## Story

Closes #{{STORY_ID}} — {{STORY_TITLE}}

Epic: #{{EPIC_ID}}

---

## Summary

- {{Bullet: what changed}}
- {{Bullet: what changed}}
- {{Bullet: why it matters}}

---

## Reviewer Focus Areas

### 🔴 High Attention Required

> These areas have security implications, complex business logic, or irreversible changes.

| File / Section | Why it needs attention |
|---|---|
| `{{path/to/file}}` | {{Specific reason}} |

### 🟡 Review Carefully

> API changes, integration points, configuration.

| File / Section | Note |
|---|---|
| `{{path/to/file}}` | {{Note}} |

### 🟢 Low Risk / Can Skim

> Tests, generated code, minor refactors, documentation.

- `tests/` — Test suite for this story
- `openapi/` — Spec updates matching implementation

---

## Test Evidence

| Layer | Count | Status |
|---|---|---|
| Unit | {{N}} | ✅ All passing |
| Integration | {{N}} | ✅ All passing |
| Acceptance | {{N}} | ✅ All passing |
| Coverage | {{N}}% | ✅ Above 80% threshold |

---

## How to Test Locally

```bash
# 1. Check out the branch
git checkout {{branch-name}}

# 2. Start dependencies
docker compose up -d

# 3. Run the service
./gradlew bootRun   # or: npm run dev

# 4. Test the feature
curl -X POST http://localhost:8080/api/v1/{{endpoint}} \
  -H "Authorization: Bearer {{test-token}}" \
  -H "Content-Type: application/json" \
  -d '{{example request body}}'

# Expected: {{expected response}}
```

---

## Checklist

- [ ] Business logic is correct per acceptance criteria
- [ ] No sensitive data exposed in logs or API responses
- [ ] Error handling is appropriate (right status codes, no stack traces in response)
- [ ] {{If migration:}} Migration is safe to run on production data (tested with real data volume)
- [ ] API changes are backwards compatible (or breaking change is intentional and versioned)
- [ ] All quality gates green in CI

---

## Breaking Changes

{{None | Describe breaking changes and migration path for consumers}}

---

## Screenshots / Demo

{{Optional: annotated screenshots or Loom link for UI changes}}
