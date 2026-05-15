# Rule: GitHub Issue Management

## Sub-issues

Use GitHub's native sub-issue API to link child issues to an epic. This surfaces a progress tracker directly on the parent issue — do not rely on text cross-references alone.

### Pattern

**Step 1 — get node IDs** for all child issues in a single GraphQL query:

```bash
gh api graphql -f query='
{
  repository(owner: "triplem", name: "kdiab") {
    i42: issue(number: 42) { id }
    i43: issue(number: 43) { id }
  }
}'
```

**Step 2 — add each child** as a sub-issue of the parent:

```bash
PARENT_ID="I_kwDO..."
for child_id in "I_kwDO..." "I_kwDO..."; do
  gh api graphql -f query="
    mutation {
      addSubIssue(input: { issueId: \"$PARENT_ID\", subIssueId: \"$child_id\" }) {
        issue { number }
        subIssue { number }
      }
    }"
done
```

Do this immediately after creating the child issues — not as a separate step later.

---

## Issue Assignment

Always assign every issue to the current user (`@me`) at creation time. Unassigned issues fall through the backlog silently.

```bash
gh issue create --title "..." --body "..." --assignee "@me"
```

When creating a batch of stories for an epic, pass `--assignee "@me"` on every `gh issue create` call. If you are creating issues on behalf of another team member, use their GitHub username instead of `@me`.

---

## Epic Documentation Checklist

Every epic **must** include documentation stories alongside the implementation stories. Add a `**documentation**` subsection in the epic's User Stories checklist.

Minimum required docs per epic:

| Type | When required | Output path |
|---|---|---|
| **ADR** | Any non-obvious architectural decision (data split, external API adoption, workflow design) | `<service>/docs/adr/ADR-NNN-<slug>.adoc` |
| **Developer reference** | Any translation layer, mapping table, or non-trivial formula | `<service>/docs/<feature>-reference.adoc` |
| **Operations guide** | Any new service or significant new infrastructure dependency | `<service>/docs/operations-guide.adoc` |
| **User/admin guide** | Any feature visible to end users or operators | `<service>/docs/user-guide.adoc` |

An epic that introduces a new service typically needs all four. An epic that adds endpoints to an existing service typically needs an ADR and a developer reference at minimum.

### What belongs in an ADR

- **Context**: why the decision was needed
- **Decision**: what was decided, in a two-tier table if settings/tiers are involved
- **Consequences**: failure modes, re-login requirements, identifier drift, any surprising behaviours
- **Alternatives considered**: what was rejected and why

### Settings that affect JWT claims

When a setting is backed by a Keycloak user attribute (i.e. it appears as a JWT claim), the ADR and user guide **must** document:
- The setting requires a re-login to take effect
- The UI must display a "takes effect on next login" notice after the setting is saved
- The write path has a synchronous dependency on Keycloak Admin API; a circuit breaker is required

---

## Epic body structure

```markdown
## User Stories

**<group>**
- [ ] #NNN — short description
...

**documentation**
- [ ] #NNN — ADR: <decision slug>
- [ ] #NNN — <developer reference title>
- [ ] #NNN — Operations guide
- [ ] #NNN — User and admin guide

---

## Acceptance Criteria
```

Update the epic body with real issue numbers immediately after creating the stories — never leave `#TBD` in a merged epic.
