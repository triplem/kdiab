# Environment Provisioning — Clarifying Questions

> Stage 4.2 (Environment Provisioning), enterprise scope, Operation phase. Lead: aidlc-aws-platform-agent
> (adopted as generic platform/environment-readiness — **no AWS**, per project rules). Support:
> devsecops, compliance. Recommendations-only intent: the "environment" is the GitHub-native delivery
> surface (repo `triplem/kdiab` + Actions runner + `review-verify.yml`/`verify.py` + `gh` auth + labels +
> branch protection). No cloud infra, VPC, IAM, or secrets to provision.
>
> Live read-only validation is already done (verify.py 10/10 green; workflow present; gh authed;
> `epic`+`In Progress` labels exist; review labels missing; `main` not branch-protected). Most delivery
> parameters were settled at Deployment Pipeline (4.1). Only one genuinely-open provisioning decision
> remains.

---

## Q1 — When to create the missing review labels

Issue materialization (deferred to Deployment Execution, 4.3) needs labels that don't yet exist on
`triplem/kdiab`: `review`, five `area:*` (clinical-safety, data-model, security, tech-debt, modernization),
three `severity:*` (high, medium, low), and `quick-win`. (`epic` and `In Progress` already exist.) When
should they be created?

- A. **Bundle into 4.3** — create the labels at Deployment Execution, immediately before the epic, so
  **all** live GitHub mutations happen in one confirmed step. This stage only inventories + validates.
  *(recommended — keeps this design/validation stage read-only, matches the deployment-pipeline Q1=D
  "defer mutation to 4.3" posture)*
- B. **Create them now** in this stage (low-risk, reversible) so the environment is fully issue-ready
  before 4.3.
- C. **Maintainer creates them manually** — document the exact `gh label create` commands as a
  prerequisite; the workflow creates nothing.
- X. Other (please specify)

[Answer]: B — Create them now. Done in this stage: 10 labels created on `triplem/kdiab` (`review`, `quick-win`, 5×`area:*`, 3×`severity:*`) via `gh label create --force` (idempotent). `epic` + `In Progress` reused (already existed). The environment is now fully issue-ready; only branch protection (Q2=A, a maintainer repo-admin action) remains as an advisory prerequisite.
