#!/usr/bin/env bash
# Hook: Stop
# Appends a session-end summary to audit/agent-log.jsonl.
# Counts actions logged this session to provide a meaningful summary.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"
AUDIT_FILE="${PROJECT_DIR}/audit/agent-log.jsonl"

SESSION_ID=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('session_id', 'unknown'))
" 2>/dev/null || echo "unknown")

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || \
  python3 -c "from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")

# Count actions logged for this session
ACTIONS_COUNT=0
if [ -f "$AUDIT_FILE" ]; then
  ACTIONS_COUNT=$(grep -c "\"session_id\": \"${SESSION_ID}\"" "$AUDIT_FILE" 2>/dev/null || echo 0)
fi

# Summarise the git state at stop time
BRANCH=$(git -C "$PROJECT_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
LAST_COMMIT=$(git -C "$PROJECT_DIR" log -1 --format="%h %s" 2>/dev/null || echo "")

ENTRY=$(python3 -c "
import json, sys
print(json.dumps({
    'ts': sys.argv[1],
    'agent': 'System',
    'session_id': sys.argv[2],
    'action': 'session_stop',
    'branch': sys.argv[3],
    'last_commit': sys.argv[4],
    'actions_this_session': int(sys.argv[5])
}, ensure_ascii=False))
" "$TIMESTAMP" "$SESSION_ID" "$BRANCH" "$LAST_COMMIT" "$ACTIONS_COUNT" 2>/dev/null)

if [ -n "$ENTRY" ] && [ -n "$AUDIT_FILE" ]; then
    mkdir -p "$(dirname "$AUDIT_FILE")"
    echo "$ENTRY" >> "$AUDIT_FILE"
fi

exit 0
