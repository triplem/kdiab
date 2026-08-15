# Project-Level Rules

> Project-specific overrides and corrections. Overrides aidlc-team.md
> and aidlc-org.md. Populated by practices-discovery and the
> self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project deviates from team-wide practice in a
> stable, durable way (e.g., "this monorepo project rebases even though
> our team default is squash"; "this legacy project skips the test
> floor because the existing suite is unsalvageable and we accept
> that").

## Way of Working

<!-- Project-specific override. Example: -->
<!-- This monorepo project rebases instead of squash-merging because -->
<!-- the per-package commit history is the audit trail we depend on -->
<!-- for partial-rollback decisions. Override applies to this project -->
<!-- only. -->

## Walking Skeleton

<!-- Project-specific override. Example: -->
<!-- This project skips the walking skeleton because we're rewriting -->
<!-- an existing service in-place — there's no greenfield bootstrap -->
<!-- to gate. -->

## Testing Posture

<!-- Project-specific override. -->

## Deployment

<!-- Project-specific override. -->

## Code Style

<!-- Project-specific override. -->

## Tech Stack

<!-- Technology choices locked for this project. -->

- **Models**: runs on Anthropic's default models via the standard Claude
  Code provider (`opus[1m]`). This project does NOT use AWS Bedrock or any
  Amazon-hosted models; `.claude/settings.json` pins
  `CLAUDE_CODE_USE_BEDROCK=0` to force the default Anthropic provider.

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

DECIDED: No AWS Bedrock and no Amazon-hosted models — use the default
Anthropic provider only. The framework's shipped Bedrock model IDs
(`ANTHROPIC_DEFAULT_*_MODEL` = `global.anthropic.*`), `AWS_REGION`, and the
four AWS MCP servers (`aws-mcp`, `aws-pricing`, `aws-iac`, `aws-serverless`)
were stripped from this project's config (PR #1546, 2026-08-15).

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

NEVER re-introduce AWS Bedrock, Amazon-hosted model IDs
(`ANTHROPIC_DEFAULT_*_MODEL` / `global.anthropic.*`), `AWS_REGION`, or the
AWS MCP servers when updating the AI-DLC framework. The `/aidlc-v2:aidlc`
installer re-adds them (merges into `settings.json`, creates `.mcp.json`);
strip them after any framework update (learned 2026-08-15).

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
