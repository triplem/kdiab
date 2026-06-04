---
name: create-adr
description: Propose, debate, and document an Architecture Decision Record for a significant technical choice. Use when a technology, framework, or architecture decision needs to be made.
argument-hint: "<decision topic>"
arguments: topic
disable-model-invocation: true
effort: high
allowed-tools: Read Write Bash(ls docs/adr/ 2>/dev/null) Bash(cat docs/requirements.md)
---

## Existing ADRs

!`ls docs/adr/ 2>/dev/null || echo "No ADRs yet"`

## Relevant NFRs from requirements

!`grep -A3 "NFR" docs/requirements.md 2>/dev/null | head -30 || echo "docs/requirements.md not found"`

## Decision topic: $topic

## Instructions

### 1 — Research

If brownfield: read existing codebase for current patterns.
Read existing ADRs in `docs/adr/` for consistency.
Identify **at least 3** candidate options — never jump straight to a recommendation.

### 2 — Analyse each option

For each option:
- Description
- Pros (concrete for this project)
- Cons (concrete for this project)
- NFR fit score (1–5 for each relevant NFR from requirements)
- Team skill risk
- Ecosystem health (actively maintained, community size)

### 3 — Agent debate

`/challenge ArchitectAgent "Debate ADR options for: $topic"`
RequirementsAgent verifies each option satisfies NFRs.
Iterate until consensus.

### 4 — Write ADR

```bash
ADR_NUM=$(printf "%04d" $(($(ls docs/adr/ 2>/dev/null | grep -cE '^[0-9]') + 1)))
```

File: `docs/adr/${ADR_NUM}-<slug>.md` using `templates/adr.md`.

### 5 — Open PR for human review

Create a PR for the ADR file. Block dependent epics/stories until the PR has comment `APPROVED`.

## Output

- `docs/adr/NNNN-<slug>.md`
- PR opened for review
- Caller unblocked once approved
