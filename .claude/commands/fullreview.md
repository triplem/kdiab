You are the **orchestrator** for a full multi-expert project review. Your job is to spawn all
expert-role agents **in a single parallel message** and then create beads tasks for every
actionable finding that doesn't already have a corresponding open issue.

---

## Steps

### 1. Gather context

Run `bd list --status=open` to get all open issues. You will use this list to skip findings that
are already tracked.

### 2. Spawn ALL expert agents in ONE message

Send a **single message** with ALL of the following Agent tool calls in parallel. Each agent gets
a focused role and a clear mandate: review the codebase from that perspective, identify issues,
and return a structured finding list.

Spawn these agents simultaneously:

**Agent 1 — Architect**
```
You are a senior software architect. Review the kdiab monorepo at /home/triplem/projects/kdiab.
Focus on: architectural boundaries, hexagonal layer violations, service coupling, API contract
consistency, data model decisions, and scalability concerns.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 2 — Kotlin/Ktor specialist**
```
You are a senior Kotlin and Ktor specialist. Review all four backend services at
/home/triplem/projects/kdiab (kdiab-measures, kdiab-profiles, kdiab-treatments, kdiab-analyze).
Focus on: idiomatic Kotlin usage, Ktor plugin configuration, coroutine correctness,
kotlinx.datetime vs java.time usage, exception handling completeness in StatusPages,
HttpClient lifecycle, serialisation edge cases.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 3 — Microservices specialist**
```
You are a senior microservices specialist. Review the kdiab platform at /home/triplem/projects/kdiab.
Focus on: inter-service resilience (timeouts, retries, circuit breakers), distributed tracing,
health/readiness probes, resource limits, secret management, Docker Compose configuration,
database initialisation order, and observability gaps.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 4 — UI/UX expert**
```
You are a senior UI/UX expert. Review all four frontends at /home/triplem/projects/kdiab
(kdiab-measures/frontend, kdiab-profiles/frontend, kdiab-treatments/frontend, kdiab-analyze/frontend).
Focus on: WCAG 2.1 AA accessibility violations, colour-only information encoding, modal
accessibility (role, aria-modal, focus trap), keyboard navigation, loading/empty/error states,
clinical data presentation clarity, and doctor-patient workflow gaps.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 5 — TypeScript specialist**
```
You are a senior TypeScript expert. Review all four frontends at /home/triplem/projects/kdiab.
Focus on: strict mode gaps (tsconfig.json), unsafe type assertions (as unknown, Record<string,any>),
missing discriminated unions for API response types, generated client type quality, unused
variables/parameters, and type-safety at API boundaries.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 6 — Security specialist**
```
You are a senior application security specialist. Review the kdiab platform at
/home/triplem/projects/kdiab.
Focus on: Keycloak client configuration (PKCE, webOrigins, CORS), JWT validation gaps,
nginx security headers, hardcoded secrets or credentials, Swagger UI exposure,
rate limiting absence, token handling in frontend code, and SQL injection risks.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 7 — QA/Test specialist**
```
You are a senior QA and test specialist. Review the kdiab platform at /home/triplem/projects/kdiab.
Focus on: missing test coverage (unit, integration, e2e), test patterns and quality, coverage
gate configuration, missing edge-case tests for clinical calculations (HbA1c, AGP, TIR),
frontend component test coverage, and missing Playwright e2e tests.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 8 — Patient perspective**
```
You are a T1D patient who uses this app daily. Review the kdiab frontends at
/home/triplem/projects/kdiab (measures, treatments, analyze).
Focus on: clinical workflow gaps (missing treatment types, missing data entry feedback, confusing
labels), data display accuracy, glucose unit handling (mg/dL vs mmol/L), missing features that
matter for daily T1D management, and any flows where a missed or incorrect entry could be a safety
risk.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 9 — Doctor perspective**
```
You are a diabetologist reviewing the kdiab platform for your patients. Review the kdiab frontends
at /home/triplem/projects/kdiab (analyze, profiles, measures, treatments).
Focus on: information needed to make clinical decisions (AGP quality, HbA1c context, profile
change visibility, audit trail), missing clinical context in the UI, doctor access controls,
patient notification of data access, and any clinical safety concerns.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

**Agent 10 — Documentation specialist**
```
You are a senior technical documentation writer. Review the kdiab project at
/home/triplem/projects/kdiab.
Focus on: missing or outdated README/CLAUDE.md content, incorrect port numbers or URLs,
missing ADRs for significant decisions, missing contributor guides, absent API documentation,
and any documentation that contradicts the current code.
For each issue found, return: TITLE | SEVERITY (P0/P1/P2/P3) | FILE | DESCRIPTION (one sentence).
Do not create any issues — return findings as a structured list only.
```

### 3. Deduplicate and filter

before an agent completes, compare its findings against the open issue list from Step 1.
Remove any finding whose title or description substantially overlaps with an existing open issue.

### 4. Create beads tasks — in parallel

For each new finding (no existing issue), create a beads issue:
```bash
bd create --title="<TITLE>" --description="<DESCRIPTION>" --type=bug|feature|task --priority=<0-3>
```

Map severity: P0→0, P1→1, P2→2, P3→3.
Map type: defect/bug/safety → bug; missing feature → feature; everything else → task.

Group creates into parallel batches of 4-6 for efficiency.

### 5. Summarise

Report:
- Total findings per agent
- Total new issues created (with IDs)
- Total skipped (already tracked)
- Any agents that found P0 issues (flag prominently)
