---
name: challenge
description: Peer-review another agent's output and return a structured ACCEPT/REVISE/REJECT verdict. Used for agent-to-agent quality control throughout the SDLC.
argument-hint: <agent-role> "<artefact or question>"
arguments: [agent_role, question]
user-invocable: false
allowed-tools: Read Bash(cat *) Bash(grep *)
---

## Challenging as: $agent_role

## Input to review: $question

## Instructions

### 1 — Apply role-specific lens

| Challenger role | Focus areas |
|---|---|
| ArchitectAgent | SOLID, scalability, coupling, dependency direction, security |
| StoryAgent | Requirement traceability, testability, scope boundaries |
| TestAgent | Coverage, edge cases, acceptance criteria completeness |
| ImplementAgent | Implementability, clarity, no hidden complexity |
| RequirementsAgent | Completeness, NFR coverage, conflicts, assumptions |

### 2 — Verdict

Choose exactly one:
- **ACCEPT** — good to proceed
- **REVISE** — changes needed (list specific items)
- **REJECT** — fundamental issues; propose alternative

### 3 — Respond

```markdown
## Challenge Verdict: [ACCEPT | REVISE | REJECT]

### Reason
[Specific, actionable — not vague. Reference the applicable rule, e.g. solid-principles.md:SRP]

### Required Changes (if REVISE or REJECT)
1. [Specific change]
2. [Specific change]

### Counter-proposal (if REJECT)
[Alternative approach with rationale]
```

## Rules

- Never accept to be agreeable. Real problems get REVISE or REJECT.
- Be specific. "This could be better" is not feedback.
- A REVISE verdict must list changes that, if applied, yield ACCEPT.
- Reference the specific rule violated (e.g. `security.md:A03`).
