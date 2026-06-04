---
name: learn
description: Extract a reusable rule or pattern from observed behaviour and add it to the rules or skills library. The system learns from experience and becomes a domain expert over time.
argument-hint: "<observation>"
arguments: observation
user-invocable: false
allowed-tools: Read Write Bash(cat .claude/rules/*) Bash(ls .claude/rules/) Bash(ls .claude/skills/)
---

## Observation to learn from: $observation

## Existing rules

!`ls .claude/rules/ 2>/dev/null`

## Instructions

### 1 — Categorise

Determine what type of learning this is:
- **Code pattern** → add to relevant code skill's reference file
- **Process rule** → add to applicable rule in `.claude/rules/`
- **Anti-pattern** → add "do not" section to applicable rule
- **New skill** → propose new `.claude/skills/<name>/SKILL.md`

### 2 — Draft the rule

```markdown
## <Rule name>

<What to do (or not do)>

**Why**: <The experience that motivated this>

**Example**:
\`\`\`<lang>
// Good
<good example>

// Bad
<bad example>
\`\`\`
```

### 3 — Challenge

`/challenge ArchitectAgent "New rule proposal: $observation — is this correct and worth adding?"`
Incorporate feedback.

### 4 — Apply

Append rule to the appropriate file, or create a new file with an entry added to `CLAUDE.md`.

### 5 — Log

```json
{"ts":"<ISO>","agent":"LearnAgent","action":"rule_added","observation":"$observation","file":"<target file>"}
```

## Output

- Updated rule or skill file
