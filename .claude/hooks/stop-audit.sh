#!/usr/bin/env bash
# Hook: Stop
# Appends a session_stop entry to the per-session JSONL file in ~/.claude/kdiab-sessions/.
# Session logs live outside the repo — no git operations needed.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

SESSION_ID=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('session_id', 'unknown'))
" 2>/dev/null || echo "unknown")

SESSION_DIR="${HOME}/.claude/kdiab-sessions"
TEMP_FILE="${SESSION_DIR}/${SESSION_ID}.jsonl"

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || \
  python3 -c "from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")

ACTIONS_COUNT=0
if [ -f "$TEMP_FILE" ]; then
  ACTIONS_COUNT=$(wc -l < "$TEMP_FILE" 2>/dev/null || echo 0)
fi

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

if [ -n "$ENTRY" ]; then
    mkdir -p "$SESSION_DIR"
    echo "$ENTRY" >> "$TEMP_FILE"
fi

exit 0
