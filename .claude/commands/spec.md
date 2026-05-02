You are about to create an **OpenSpec** for one or more beads issues. This is the mandatory planning phase before any implementation work begins.

Issue ID(s): $ARGUMENTS

---

## Steps

**1. Read each issue**

For each issue ID in `$ARGUMENTS`, run `bd show <id>` and read the full description, design notes, and any existing context.

**2. Enter plan mode**

Call `EnterPlanMode` now. In plan mode you may only read — no file edits, no shell writes. Use this phase to:
- Read all source files referenced by or relevant to the issue
- Understand the current architecture (hexagonal layers, existing patterns)
- Identify every file that must change
- Note any OpenAPI contract changes required

**3. Write the OpenSpec to the plan file**

Structure the plan as follows for each issue:

```
# OpenSpec: <issue title>  (<issue-id>)

## Goal
What is being built and why. One concise paragraph.

## Context
Current state: which files, functions, or DB tables are involved and what they currently do.

## Interface Changes
- api/openapi.yaml: list any endpoint additions, schema changes
- Domain model: new types, changed function signatures, new DB columns
- If none: "No interface changes."

## Implementation Plan
Numbered, ordered steps. Each step names the exact file path and what changes.
1. `path/to/file.kt` — add/change X
2. `path/to/file.kt` — update Y
...

## Test Plan
- [ ] Unit: what to test with MockK/JUnit5
- [ ] Integration: what to verify end-to-end in H2
- [ ] Quality gate: `./gradlew :backend:check` or `npm run build`

## Acceptance Criteria
- [ ] Criterion 1 (observable, verifiable)
- [ ] Criterion 2

## Risks & Assumptions
- Risk or assumption → mitigation or why it is safe to assume
```

**4. Exit plan mode**

Call `ExitPlanMode`. The user will review and approve the plan.

**5. After approval — store the spec in beads**

For each issue, write the approved OpenSpec back into the issue so parallel agents can consume it:

```bash
bd update <id> --design="<full openspec content>"
```

This makes the issue ready for `/parallel` to pick up. Verify with `bd show <id>` that the design field is populated.
