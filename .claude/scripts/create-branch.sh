#!/usr/bin/env bash
# Create a feature branch following the project naming convention.
# Usage: ./scripts/create-branch.sh <type> <issue-number> <short-description>
# Example: ./scripts/create-branch.sh feature 42 "jwt-refresh-token"

set -euo pipefail

TYPE="${1:?Usage: $0 <type> <issue-number> <description>}"
ISSUE="${2:?Usage: $0 <type> <issue-number> <description>}"
DESC="${3:?Usage: $0 <type> <issue-number> <description>}"

VALID_TYPES="feature fix bug chore docs refactor"
if ! echo "$VALID_TYPES" | grep -qw "$TYPE"; then
  echo "Error: type must be one of: $VALID_TYPES"
  exit 1
fi

SLUG=$(echo "$DESC" | tr '[:upper:]' '[:lower:]' | tr ' ' '-' | sed 's/[^a-z0-9-]//g')
BRANCH="${TYPE}/${ISSUE}-${SLUG}"

echo "Creating branch: $BRANCH"
git checkout main
git pull origin main
git checkout -b "$BRANCH"
echo "Branch '$BRANCH' created and checked out."
