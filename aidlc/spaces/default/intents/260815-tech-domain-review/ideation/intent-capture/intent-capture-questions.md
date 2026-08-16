# Intent Capture — Clarifying Questions

**Intent:** review technology and domain and suggest improvements
**Scope:** enterprise (comprehensive depth)
**Project:** kdiab — T1D (Type 1 Diabetes) management platform (brownfield, 9-component Kotlin/Ktor + React monorepo)
**Mode:** self-guided (user edited the file directly; answers collected 2026-08-15T19:21:27Z)

> This is an **assessment** initiative: the goal is to review the existing platform's technology and
> T1D domain model and produce prioritized improvement recommendations. Questions below frame *what*
> review you want and *how* you'll judge it valuable. Answer with the option letter(s); every question
> ends with `X. Other`. Multi-select questions say "(select all that apply)".

---

## Q1. Which parts of the platform should the review prioritize? (select all that apply)
- A. Backend services & architecture (Kotlin/Ktor, hexagonal layering, inter-service comms, BFF pattern)
- B. Frontend (React 19 / TypeScript, `kdiab-ui` SPA)
- C. Data model & persistence (PostgreSQL / Exposed / Liquibase, per-service DBs)
- D. Domain correctness & T1D clinical safety (`kdiab-calc`, `kdiab-profiles`, `kdiab-treatments`)
- E. Cross-cutting: security, observability, CI/CD, testing
- X. Other (please specify)

[Answer]: A, B, C, D AND E, watch out for test pyramid, static code analysis issues, code duplications, etc.

## Q2. What is the primary goal / desired outcome of the review?
- A. Reduce technical debt & improve maintainability
- B. Modernize / upgrade the tech stack
- C. Improve T1D domain & clinical correctness / safety
- D. Harden security & compliance (health data / PII)
- E. Improve performance & scalability
- X. Other (please specify)

[Answer]: priorities should be c and also d, later also a and b.

## Q3. Who is the primary audience for the review output?
- A. Solo maintainer (you)
- B. A small dev team / future contributors
- C. External stakeholders (investors, clinical advisors)
- D. Auditors / compliance reviewers
- X. Other (please specify)

[Answer]: A

## Q4. What triggered this review now?
- A. Preparing to scale / onboard real users
- B. Accumulated tech debt / development friction
- C. Preparing to onboard collaborators
- D. Periodic health-check / curiosity
- E. A specific incident or pain point
- X. Other (please specify)

[Answer]: D

## Q5. Which technology dimensions matter most? (select all that apply)
- A. Stack currency (framework / library versions, deprecations)
- B. Architecture & service boundaries (are the 9 services the right split?)
- C. Testing strategy & coverage (unit/integration/e2e, Kover floors)
- D. CI/CD & release process (GitHub Actions, semantic-release, Docker publish)
- E. Observability & operability (OTEL, tracing, logging, runbooks)
- X. Other (please specify)

[Answer]: A, B, C, D, E.

## Q6. Which domain dimensions matter most? (select all that apply)
- A. Clinical correctness (dose calculation, basal profiles, CGM/BGM interpretation)
- B. Safety guardrails (bolus limits, correction bounds, alerting)
- C. Data-model completeness (measures / treatments / carbs coverage vs. real T1D workflows)
- D. Interoperability (Nightscout API, AAPS, xDrip+, Juggluco compatibility)
- E. Terminology & standards alignment (units mg/dL vs mmol/L, ISO / HL7 / FHIR)
- X. Other (please specify)

[Answer]: A, B, C

## Q7. What constraints or non-negotiables must the recommendations respect? (select all that apply)
- A. Must stay Kotlin/Ktor backend + React frontend
- B. Must stay self-hostable (Docker / Podman; no mandatory managed cloud)
- C. No breaking changes to existing public APIs / Nightscout compatibility
- D. Solo-maintainer-friendly (low operational overhead)
- E. No hard constraints — open to anything, including rewrites
- X. Other (please specify)

[Answer]: E

## Q8. What does a successful review deliver? (measurable outcome)
- A. A prioritized backlog of concrete improvement issues (GitHub issues)
- B. ADRs for the top architectural decisions
- C. A phased improvement roadmap
- D. A short set of quick wins to implement immediately
- E. All of the above
- X. Other (please specify)

[Answer]: A, D, C

## Q9. Should improvements be implemented in this workflow, or recommendations-only for now?
- A. Recommendations only — I'll decide what to implement later
- B. Implement quick wins now, defer the rest
- C. Full implementation through the enterprise lifecycle (all 32 stages)
- X. Other (please specify)

[Answer]: A
