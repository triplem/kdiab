# As a {{ROLE}}, I want to {{CAPABILITY}} so that {{BENEFIT}}

**Story ID:** {{TRACKER_ID}}
**Epic:** {{EPIC_ID}}
**Status:** pending-approval | approved | in-progress | review | done
**Estimate:** S (1d) | M (3d) | L (5d)
**Branch:** `{{type}}/{{STORY_ID}}-{{slug}}`
**PR:** {{PR_URL when created}}
**Linked Requirements:** {{FR-001, NFR-002}}

---

## Context

{{Why does this story exist? What problem does it solve? Link to the epic and any relevant requirements or ADRs.}}

---

## Acceptance Criteria

```gherkin
Scenario: Happy path
  Given {{precondition}}
  When {{action}}
  Then {{expected outcome}}
  And {{additional assertion}}

Scenario: Not found
  Given {{precondition}}
  When {{action with non-existent resource}}
  Then {{404 or domain error}}

Scenario: Validation error
  Given {{precondition}}
  When {{action with invalid input}}
  Then {{validation error response}}
```

---

## Technical Notes

### API Changes

```yaml
# OpenAPI sketch — full spec to be written in openapi.yaml
POST /api/v1/users:
  request: { email, name }
  response: { id, email, name, createdAt }
  errors: 400 (validation), 409 (duplicate)
```

### Data Model Changes

```sql
-- New table / column changes
ALTER TABLE users ADD COLUMN verified_at TIMESTAMP;
```

### Integration Points

- Calls: {{external service / repo}}
- Publishes: {{event name}} to {{topic}}
- Consumes: {{event}} from {{topic}}

---

## Definition of Done

- [ ] All acceptance criteria implemented
- [ ] All acceptance criteria covered by automated tests
- [ ] Unit test coverage ≥ 80% for new code in this story
- [ ] Integration tests for DB/HTTP interactions
- [ ] SAST scan clean
- [ ] OpenAPI spec updated
- [ ] Linting passes
- [ ] PR approved
- [ ] Squash-merged to main
- [ ] Release published
- [ ] Story closed

---

## Dependencies

{{Other story IDs this must come after. "None" if independent.}}

---

## Notes / Decisions

{{Any decisions made during implementation — agent or human. Running log.}}
