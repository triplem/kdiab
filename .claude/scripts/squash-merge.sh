#!/usr/bin/env bash
# Squash-merge a feature branch into main.
# Usage: ./.claude/scripts/squash-merge.sh <branch> "<conventional-commit-message>"
# Example: ./.claude/scripts/squash-merge.sh feature/42-jwt-refresh "feat(auth): add JWT refresh token (#42)"

set -euo pipefail

BRANCH="${1:?Usage: $0 <branch> '<commit-message>'}"
MESSAGE="${2:?Usage: $0 <branch> '<commit-message>'}"

echo "Squash-merging $BRANCH into main..."
git checkout main
git pull origin main
git merge --squash "$BRANCH"
git commit -m "$MESSAGE"
git push origin main
git branch -d "$BRANCH" 2>/dev/null && echo "Deleted local branch $BRANCH" || true
git push origin --delete "$BRANCH" 2>/dev/null && echo "Deleted remote branch $BRANCH" || true
echo "Done. Squash-merged '$BRANCH' into main."
