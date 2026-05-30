# Rule: Branching Strategy

## Main Branches

| Branch | Purpose | Protection |
|---|---|---|
| `main` | Production-ready code | Protected: require PR, CI green, squash merge only |
| `develop` | Integration branch (optional, for large teams) | Protected: require PR, CI green |

**Direct commits to `main` are blocked by a git hook — this is enforced, not just a convention.**
Never commit directly to `main` or `develop`.

## Creating a Feature Branch

Always create a branch before staging any commits. Use the project script:

```bash
./.claude/scripts/create-branch.sh <type> <issue-number> <short-description>
# Example:
./.claude/scripts/create-branch.sh docs 321 pr-reviewer-skill
```

The script validates the type, slugifies the description, checks out `main`, pulls latest, and creates the branch in one step.

If the script fails due to unstaged changes (error: `cannot pull with rebase: You have unstaged changes`), stash first, pull, then branch:

```bash
git stash
git pull origin main
git checkout -b <type>/<issue-number>-<short-description>
git stash pop
# then stage and commit normally
git push -u origin <branch>
```

Do **not** attempt to commit on `main` and then move the commit — always create the branch first.

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

## Post-Merge Branch Cleanup

After every PR is merged, **always** delete both the remote and local branch immediately:

```bash
# Delete remote branch
git push origin --delete <branch-name>

# Switch away from the branch first if on it
git checkout main

# Delete local branch
git branch -D <branch-name>
```

Do this as the last step of every `gh pr merge` flow — stale branches clutter `git branch` output and confuse future work. Do not rely on GitHub's "Delete branch on merge" auto-delete alone; always delete the local branch too.

## Stale Branches

Any branch that has been merged and not yet deleted is stale. Clean up proactively:

```bash
git fetch --prune                    # removes stale remote-tracking refs
git branch -vv | grep ': gone]'      # shows local branches whose remote is deleted
```

## Worktree Cleanup: Double-Force Required for Locked Worktrees

Claude agent worktrees are locked with a lock reason. A single `git worktree remove --force` is not enough — it fails with `"cannot remove a locked working tree, use 'remove -f -f' to override"`. Always use double-force:

```bash
git worktree remove -f -f /path/to/worktree
```

After removing worktrees, delete the now-dangling local branches:

```bash
git branch | grep "worktree-agent-" | xargs -r git branch -D
```

Then prune stale remote-tracking refs:

```bash
git fetch --prune
```

## Hotfix

For production bugs requiring immediate fix:

```
fix/<issue-number>-hotfix-<description>
```

Branch from `main`, fix, PR to `main`. ReleaseAgent creates a patch release immediately.
