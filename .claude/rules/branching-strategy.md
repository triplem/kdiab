# Rule: Branching Strategy

## Main Branches

| Branch | Purpose | Protection |
|---|---|---|
| `main` | Production-ready code | Protected: require PR, CI green, squash merge only |
| `develop` | Integration branch (optional, for large teams) | Protected: require PR, CI green |

Never commit directly to `main` or `develop`.

## Feature Branches

```
<type>/<issue-number>-<short-description>
```

- `type` must match the conventional commit type: `feature`, `fix`, `bug`, `chore`, `docs`, `refactor`
- `issue-number` is the tracker issue ID (GitHub #, Jira, GitLab !)
- `short-description` is 2–5 words, kebab-case, describing the work

Examples:
```
feature/42-user-jwt-auth
fix/101-null-pointer-on-login
bug/77-payment-timeout-retry
chore/55-upgrade-spring-boot-3
docs/12-openapi-auth-endpoints
refactor/88-extract-domain-events
```

## Worktrees for Parallel Agents

When multiple agents work on the same story:

```bash
# Agent A
git worktree add ../worktree-42-impl feature/42-user-jwt-auth

# Agent B (test writer)
git worktree add ../worktree-42-tests feature/42-user-jwt-auth
```

Each agent commits to the same branch via its own worktree. Coordinate via the feature branch — rebase frequently.

## Merge Policy

- **main ← feature**: Squash merge. One commit per story.
- **develop ← feature**: Merge commit (preserves history during integration).
- **Never**: merge main into a feature branch. Rebase instead: `git rebase main`.

## Release Tags

```
v<major>.<minor>.<patch>
```

Tags are created on `main` by ReleaseAgent after squash merge. Semver bump is determined automatically from conventional commits.

## Stale Branches

Feature branches are deleted automatically after PR merge (GitHub: "Delete branch on merge"). Worktrees are cleaned up by the agent that created them.

## Hotfix

For production bugs requiring immediate fix:

```
fix/<issue-number>-hotfix-<description>
```

Branch from `main`, fix, PR to `main`. ReleaseAgent creates a patch release immediately.
