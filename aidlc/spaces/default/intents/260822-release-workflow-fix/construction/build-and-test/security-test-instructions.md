# Security Test Instructions — Release Workflow Fix (#1617)

Consumes `../release-workflow-fix/code-generation/code-summary.md`. DevSecOps perspective
(aidlc-devsecops-agent).

## Threat review of the change

| Concern | Assessment |
|---|---|
| **Shell injection** in the derive step | ✅ Safe. `inputs.service` is passed via `env: SERVICE`, and the run script uses the shell variable `${SERVICE#kdiab-}` — the value is **not** interpolated as a GitHub Actions expression into the script text, so a hostile `inputs.service` cannot break out. (Reviewer-confirmed.) |
| New secrets / tokens | ✅ None added. |
| New permissions / `GITHUB_TOKEN` scope | ✅ Unchanged — no `permissions:` change. |
| New external action | ✅ None — only a `run:` step; existing action pins (SHAs) untouched. |
| Supply-chain / artifact integrity | ✅ Only the artifact **name** changes; contents (`path:`) and the Trivy scan step are unchanged. |
| Secrets/PII in logs | ✅ The step echoes only a service slug (e.g. `measures`) — non-sensitive. |

## How to verify

- Confirm `env: SERVICE: ${{ inputs.service }}` + `run: ...${SERVICE#kdiab-}...` (not `${{ inputs.service }}`
  inline in `run`).
- `git diff` shows no `permissions:`, secret, or action-pin change.
- CI: CodeQL `Analyze (actions)` runs on the workflow change (a `.github/workflows` file changed).

## Verdict

**No new security risk.** The change is name-only, injection-safe, and adds no secret, permission, or
dependency.
