# [EPIC] {{DOMAIN}} — {{CAPABILITY}}

**Epic ID:** {{TRACKER_ID}}
**Status:** pending-approval | approved | in-progress | done
**Milestone:** M{{N}}
**Complexity:** S | M | L | XL
**Linked Requirements:** {{FR-001, FR-002, NFR-003}}
**Dependencies:** {{Other epic IDs, or "none"}}

---

## Goal

{{1–2 sentences describing the user value delivered by this epic. Written from the user's perspective.}}

---

## Scope

### In Scope
- {{Feature / capability}}
- {{Feature / capability}}

### Out of Scope
- {{Explicitly excluded to prevent scope creep}}

---

## Acceptance Criteria

```gherkin
Given {{precondition}}
When {{action}}
Then {{expected outcome}}

Given {{precondition}}
When {{edge case action}}
Then {{expected outcome}}
```

---

## Definition of Done

- [ ] All child user stories complete and accepted
- [ ] All acceptance criteria verified by automated tests
- [ ] Unit test coverage ≥ 80% for new code
- [ ] Integration tests passing
- [ ] SAST scan clean
- [ ] OpenAPI spec updated (if applicable)
- [ ] Documentation updated
- [ ] Released and deployed to production

---

## Technical Notes

{{High-level technical considerations: new services needed, schema changes, integration points. Not a design doc — that's in ADRs.}}

---

## ADRs

{{Links to any ADRs that were created for decisions in this epic. "None" if not applicable.}}

---

## Child Stories

{{Links to child user story issues. Added by StoryAgent after decomposition.}}

---

## Approval