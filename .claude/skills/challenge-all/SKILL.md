---
name: challenge-all
description: Run all specialist reviewer perspectives (doctor, patient, security, QA, architect, DevOps, UX, requirements, performance, operations, technical writer) against a single target and produce a consolidated challenge report. Use before merging a significant feature or finalising an epic.
argument-hint: <pr-number | issue-number | "inline text to review">
arguments: [target]
user-invocable: true
allowed-tools: Read Bash(gh *) Bash(grep -r *) Bash(git diff *) Bash(find *)
---

## Multi-perspective challenge: $target

!`gh issue view $target 2>/dev/null || gh pr view $target 2>/dev/null || echo "Reviewing inline: $target"`

!`gh pr diff $target 2>/dev/null | head -4000`

## Instructions

You embody all specialist roles simultaneously. For each persona, apply that lens to the target and record findings. Then synthesise into a consolidated verdict. A single REJECT from any persona blocks the target.

Work through each persona in order. Findings from one persona may inform another — consistency across perspectives is as important as depth within each.

---

## Persona 1: T1D Specialist Doctor
*Clinical correctness, safety, medical workflow*

Apply the checklist from `/doctor-t1d-review`:
- Glucose thresholds correct (hypo < 3.9 mmol/L / 70 mg/dL)?
- Dose recommendations clearly labelled as suggestions?
- TIR zones and AGP follow consensus standards?
- Units handled correctly?

---

## Persona 2: T1D Patient (pump + CGM)
*Usability, real-world fit, trust*

Apply the checklist from `/patient-t1d-review`:
- Understandable in < 30 seconds without a manual?
- Works on mobile?
- Graceful failure when backend is slow or down?
- APS/Nightscout data handled correctly?

---

## Persona 3: Security Specialist
*OWASP Top 10, auth, secrets, injection*

Apply the checklist from `/security-review`:
- Every endpoint authenticated; access control checked?
- No secrets in source or logs?
- No injection vectors?
- CSP / security headers correct?

---

## Persona 4: QA Engineer
*Test coverage, edge cases, regression risk*

Apply the checklist from `/qa-review`:
- All acceptance criteria testable and tested?
- Boundary values, error paths, null inputs covered?
- No tests skipped or commented out?

---

## Persona 5: Software Architect
*Hexagonal layers, SOLID, coupling, API design*

Apply the checklist from `/architect-review`:
- Domain layer free of framework imports?
- No N+1 or over-fetching?
- Breaking changes versioned?
- No premature abstraction?

---

## Persona 6: DevSecOps Engineer
*Container hygiene, env config, CI/CD, supply chain*

Apply the checklist from `/devops-review`:
- Base images pinned?
- OTEL env vars present on all backends?
- New services in correct depends_on chain?
- Secrets never in build args?

---

## Persona 7: UI/UX and Accessibility Specialist
*WCAG AA, ease of use, interaction design*

Apply the checklist from `/ux-review`:
- Contrast ratio ≥ 4.5:1?
- All inputs have labels (not just placeholders)?
- Touch targets ≥ 44px?
- Keyboard navigation works?

---

## Persona 8: Requirements Engineer
*Completeness, testability, consistency, NFRs*

Apply the checklist from `/requirements-review`:
- All criteria SMART and unambiguous?
- NFRs stated (performance, availability)?
- No contradictions with existing ADRs?

---

## Persona 9: Performance Engineer
*Query efficiency, bundle size, response times*

Apply the checklist from `/performance-review`:
- No N+1 queries?
- Pagination enforced?
- Parallel upstream calls in kdiab-analyze?
- Bundle size impact assessed?

---

## Persona 10: Operations / SRE
*Observability, graceful degradation, runbook*

Apply the checklist from `/operations-review`:
- OTEL traces cover new code paths?
- Upstream failures produce 502, not hang?
- New env vars have safe defaults?

---

## Persona 11: Technical Writer
*Documentation, clarity, in-app help text*

Apply the checklist from `/technical-writer-review`:
- ADRs complete (Context / Decision / Consequences / Alternatives)?
- User guide written for the correct audience?
- Error messages explain what to do next?

---

## Consolidated report format

```markdown
## Challenge-All Report: $target

**Overall verdict**: ACCEPT | REVISE | REJECT

A single REJECT from any persona = overall REJECT.
Any REVISE from a persona = overall REVISE (at minimum).

---

### Persona verdicts

| Persona | Verdict | Top finding |
|---|---|---|
| T1D Doctor | ACCEPT/REVISE/REJECT | [one-liner] |
| T1D Patient | ACCEPT/REVISE/REJECT | [one-liner] |
| Security | ACCEPT/REVISE/REJECT | [one-liner] |
| QA | ACCEPT/REVISE/REJECT | [one-liner] |
| Architect | ACCEPT/REVISE/REJECT | [one-liner] |
| DevOps | ACCEPT/REVISE/REJECT | [one-liner] |
| UX/Accessibility | ACCEPT/REVISE/REJECT | [one-liner] |
| Requirements | ACCEPT/REVISE/REJECT | [one-liner] |
| Performance | ACCEPT/REVISE/REJECT | [one-liner] |
| Operations | ACCEPT/REVISE/REJECT | [one-liner] |
| Technical Writer | ACCEPT/REVISE/REJECT | [one-liner] |

---

### Consolidated required changes (REVISE)
1. [Change] — raised by [Persona]
2. [Change] — raised by [Persona]

### Blockers (REJECT)
- [Blocker] — raised by [Persona] — must be resolved before proceeding

### Cross-cutting observations
- [Pattern spotted by multiple personas — systemic issue or strength]

### Positive consensus
- [What multiple personas agreed is well done]
```

