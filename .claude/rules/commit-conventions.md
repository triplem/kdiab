# Rule: Commit Conventions

All commits must follow [Conventional Commits](https://www.conventionalcommits.org/) with the Angular preset.

## Format

```
<type>(<scope>): <short summary>

[optional body — wrap at 72 chars]

[optional footer: BREAKING CHANGE: ..., Closes #N, Refs #N]
```

## Types

| Type | When | Triggers release |
|---|---|---|
| `feat` | New feature | minor |
| `fix` | Bug fix | patch |
| `perf` | Performance improvement | patch |
| `refactor` | Code change (no feature/fix) | — |
| `test` | Tests only | — |
| `docs` | Documentation only | — |
| `style` | Formatting, whitespace | — |
| `build` | Build system, dependencies | — |
| `ci` | CI/CD configuration | — |
| `chore` | Other (release scripts, etc.) | — |
| `revert` | Revert a prior commit | patch |

Breaking change: append `!` to type or add `BREAKING CHANGE:` in footer → major bump.

## Scope

The scope is the domain area affected: `auth`, `users`, `orders`, `api`, `db`, `config`.

Use the same scope consistently for a feature area. Check existing commits for precedent.

## Summary Rules

- Imperative mood: "add", "fix", "update" (not "added", "fixes", "updating")
- No period at the end
- Max 72 characters
- Lowercase first letter

## Examples

```
feat(auth): add JWT refresh token rotation

fix(users): return 404 when user not found instead of 500

refactor(orders): extract discount calculation to domain service

test(auth): add integration tests for token expiry

docs(api): document rate limiting headers

feat(billing)!: remove deprecated /v1/invoices endpoint

BREAKING CHANGE: Clients must migrate to /v2/invoices before upgrading.
Closes #84
```

## Squash Merge Policy

When merging a PR to main, squash all commits. The squash commit message must be the story-level conventional commit:

```
feat(scope): story title (#story-id)
```

Individual commits during development may be informal, but the squash message must be clean.

## Tooling

```bash
# Validate locally before push
npx commitlint --from HEAD~1 --to HEAD

# Interactive commit helper
npx git-cz
```

Add `commitlint` as a commit-msg hook in `.husky/commit-msg`.
