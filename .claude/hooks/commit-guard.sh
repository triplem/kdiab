#!/usr/bin/env bash
# Hook: PreToolUse:Bash(git commit *)
# Enforces: conventional commit message format, no direct commits to main/master.

INPUT=$(cat)

COMMAND=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('command',''))" 2>/dev/null || echo "")

# Only apply to git commit commands
if ! echo "$COMMAND" | grep -qE '^\s*git commit'; then
  exit 0
fi

deny() {
  python3 -c "
import json, sys
print(json.dumps({
    'hookSpecificOutput': {
        'hookEventName': 'PreToolUse',
        'permissionDecision': 'deny',
        'permissionDecisionReason': sys.argv[1]
    }
}))
" "$1"
  exit 0
}

inject() {
  python3 -c "
import json, sys
print(json.dumps({
    'hookSpecificOutput': {
        'hookEventName': 'PreToolUse',
        'permissionDecision': 'allow',
        'permissionDecisionReason': 'Allowed with warning',
    },
    'systemMessage': sys.argv[1]
}))
" "$1"
  exit 0
}

# Block: committing directly to main or master
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
if [ "$CURRENT_BRANCH" = "main" ] || [ "$CURRENT_BRANCH" = "master" ]; then
  deny "Direct commit to '${CURRENT_BRANCH}' is blocked. Create a feature branch first: ./.claude/scripts/create-branch.sh <type> <issue-id> <description>"
fi

# Extract commit message from -m flag
MSG=""
if echo "$COMMAND" | grep -q -- '-m'; then
  # Handle: git commit -m "message" or git commit -m 'message'
  MSG=$(echo "$COMMAND" | sed -n "s/.*-m ['\"\`]\([^\`'\"]*\)['\"\`].*/\1/p")
fi

if [ -n "$MSG" ]; then
  # Conventional commit pattern: type(scope): summary  OR  type!: summary  OR  type: summary
  PATTERN='^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\([a-z0-9_/-]+\))?(!)?: .{1,72}$'
  if ! echo "$MSG" | grep -qE "$PATTERN"; then
    deny "Commit message does not follow Conventional Commits format.

Expected: <type>(<scope>): <summary>
Types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert

Your message: \"${MSG}\"

Examples:
  feat(auth): add JWT refresh token rotation
  fix(users): return 404 when user not found
  test(auth): add integration tests for token expiry"
  fi

  # Check summary length
  SUMMARY=$(echo "$MSG" | sed 's/^[^:]*: //')
  if [ ${#SUMMARY} -gt 72 ]; then
    inject "Warning: commit summary is ${#SUMMARY} characters. Keep it under 72. Consider moving details to the body."
  fi
fi

exit 0
