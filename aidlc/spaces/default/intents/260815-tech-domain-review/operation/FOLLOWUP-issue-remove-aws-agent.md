## Request

Remove the **`aidlc-aws-platform-agent`** persona from this project's AI-DLC install — this project has
**no AWS infrastructure** (project rule: no AWS Bedrock, no Amazon-hosted models, no AWS MCP servers; the
Bedrock/AWS config was stripped in PR #1546). An AWS solutions-architect persona has nothing to do here.

> Original request: _"open a new issue, since we do not have an aws infra, remove the
> aidlc-aws-platform-agent"_

## Evidence it's inapplicable

On the just-completed `tech-domain-review` workflow (a recommendations-only assessment), the agent had no
AWS to design: `infrastructure-design` (3.4) was **skipped**, and `environment-provisioning` (4.2) and
`feedback-optimization` (4.7) had to adopt the persona as "generic platform / **no cloud spend**". Its
AWS specialism was never exercised.

## Impact — it is wired into 6 stages (re-wiring required, not a simple delete)

`.claude/agents/aidlc-aws-platform-agent.md` is referenced by the compiled stage graph:

| Stage | Role | Suggested reassignment |
|---|---|---|
| feasibility (1.3) | support | drop, or → aidlc-architect-agent |
| application-design (2.6) | support | → aidlc-architect-agent (already lead) |
| nfr-design (3.3) | support | → aidlc-architect-agent (already lead) |
| infrastructure-design (3.4) | **lead** | → aidlc-devsecops-agent or aidlc-architect-agent (or retire the stage for non-cloud projects) |
| environment-provisioning (4.2) | **lead** | → aidlc-operations-agent (or aidlc-devsecops-agent) |
| feedback-optimization (4.7) | support | drop, or → aidlc-operations-agent (already lead) |

Removal steps:
1. Delete `.claude/agents/aidlc-aws-platform-agent.md`.
2. Reassign the `lead_agent`/`support_agents` in the 6 stage definitions above.
3. Recompile: `bun .claude/tools/aidlc-graph.ts compile`.
4. Regenerate runners + fix drift guards: `bun .claude/tools/aidlc-runner-gen.ts write` and
   `bun .claude/tools/aidlc-utility.ts scope-table` (+ their `--check` guards must pass).
5. Grep for stragglers: `grep -rn "aidlc-aws-platform-agent" .claude/`.

## Installer caveat (same class as the Bedrock strip)

The `/aidlc-v2:aidlc` installer will **re-add** the agent on the next framework update (it re-installs the
framework-owned files), exactly like it re-adds Bedrock/AWS config. So this removal must be **re-applied
after every framework update**, and documented in `aidlc/spaces/default/memory/project.md` under
`## Forbidden` (cross-reference the existing no-Bedrock rule).

## Acceptance criteria

- [ ] `aidlc-aws-platform-agent.md` removed; the 6 stages re-wired.
- [ ] `aidlc-graph.ts compile`, `aidlc-runner-gen.ts check`, `scope-table --check` all pass.
- [ ] No remaining `aidlc-aws-platform-agent` references in `.claude/`.
- [ ] `project.md ## Forbidden` documents the removal + the re-apply-after-update note.
- [ ] Decide the fate of `infrastructure-design` (3.4) for a no-cloud project (reassign lead vs retire).

_Filed to capture the request (per user). The actual removal is a follow-up change once approved._
