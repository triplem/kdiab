---
name: audit
description: Review the agent audit log to extract insights, patterns, and improvement recommendations. Use periodically or after a milestone completes.
disable-model-invocation: true
allowed-tools: Read Write Bash(cat audit/*) Bash(jq *)
---

## Agent log (last 100 entries)

!`tail -100 audit/agent-log.jsonl 2>/dev/null | jq -r '[.ts,.agent,.action,.verdict//"",.reason//""] | @tsv' 2>/dev/null || cat audit/agent-log.jsonl 2>/dev/null | tail -50 || echo "No agent log found"`

## Human decisions log

!`tail -50 audit/human-decisions.jsonl 2>/dev/null | jq -r '[.ts,.actor,.action,.artefact//""] | @tsv' 2>/dev/null || cat audit/human-decisions.jsonl 2>/dev/null | tail -20 || echo "No human decisions log found"`

## Instructions

### 1 — Analyse

Identify:
1. **Retry patterns** — which task types trigger the most retries?
2. **Human escalation frequency** — are agents escalating too often/rarely?
3. **Challenge outcomes** — which agent pairs have the most REVISE/REJECT cycles?
4. **Quality gate failures** — which gates fail most often?
5. **Velocity** — average time from story approved to PR merged
6. **Recurring patterns** — code patterns that should become rules

### 2 — Report

Write `audit/report-<DATE>.md`:

```markdown
# Audit Report — <DATE>

## Velocity
- Stories completed: N | Avg cycle: Xd

## Agent Health
- Total retries: N (target: <2/story)
- Human escalations: N (target: <1/story)
- Top retry cause: <cause>

## Quality Gate Failures
| Gate | Failures | Most common cause |
|---|---|---|

## Learnings
- <Pattern>: consider adding rule

## Recommendations
1. <Actionable>
```

### 3 — Extract learnings

For each recurring pattern found → invoke `/learn "<pattern description>"`.

## Output

- `audit/report-<DATE>.md`
- Optionally triggers `/learn`
