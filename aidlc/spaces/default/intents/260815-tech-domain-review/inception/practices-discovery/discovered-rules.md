# Discovered Rules — kdiab

Corrective, agent-facing hard constraints derived from interview answers and evidenced
team practice. One rule per line. Promoted (append) to the project rule layer
(`## Mandated` / `## Forbidden`) on affirmation.

## Mandated

- ALWAYS create a feature branch (`<type>/<issue>-<description>`) before staging any commit; never work on `main` directly.
- ALWAYS merge PRs to `main` with a merge-commit (never squash) to preserve `Closes #N` issue linkage.
- ALWAYS pass the full quality gate before opening or merging a PR: `./gradlew check` (tests + Detekt + Kover 80%) for Kotlin services, and `npm run build` + lint + test for kdiab-ui.
- ALWAYS keep line coverage at or above 80% on new and modified code, across every scope.
- ALWAYS wait for every GitHub Actions check to be green before merging to `main`.
- ALWAYS reference a GitHub issue in commit messages and follow Conventional Commits (Angular preset).
- ALWAYS delete both the remote and local feature branch immediately after a PR merges.

## Forbidden

- NEVER commit directly to `main` (git-hook enforced).
- NEVER squash-merge — it drops the per-commit `Closes #N` issue linkage.
- NEVER merge a PR with a failing or still-running CI check, and never bypass required checks with `--admin`.
- NEVER hardcode credentials, secrets, or API keys, and never log secrets or raw PII.
