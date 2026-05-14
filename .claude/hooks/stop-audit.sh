#!/usr/bin/env bash
# Hook: Stop
# Appends a session-end summary to the audit log when Claude stops responding.
# Uses asyncRewake=false — runs in background, does not block.

INPUT=$(cat)
PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(pwd)}"

SESSION_ID=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('session_id', 'unknown'))
" 2>/dev/null || echo "unknown")

AUDIT_FILE="${PROJECT_DIR}/audit/agent-log.jsonl"

# Extract what was done this turn from the transcript (best-effort)
TRANSCRIPT=$(echo "$INPUT" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('transcript_path', ''))
" 2>/dev/null || echo "")

TOOLS_USED=""
if [ -n "$TRANSCRIPT" ] && [ -f "$TRANSCRIPT" ]; then
  TOOLS_USED=$(tail -50 "$TRANSCRIPT" 2>/dev/null | \
    python3 -c "
import sys, json
lines = sys.stdin.readlines()
tools = set()
for line in lines:
    try:
        d = json.loads(line)
        if d.get('type') == 'tool_use':
            tools.add(d.get('name',''))
    except Exception:
        pass
print(', '.join(sorted(tools)) if tools else '')
" 2>/dev/null || echo "")
fi

TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || python3 -c "from datetime import datetime,timezone; print(datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ'))")

ENTRY=$(python3 -c "
import json, sys
print(json.dumps({
    'ts': sys.argv[1],
    'agent': 'System',
    'session_id': sys.argv[2],
    'action': 'session_stop',
    'tools_used': sys.argv[3]
}))
" "$TIMESTAMP" "$SESSION_ID" "$TOOLS_USED" 2>/dev/null || echo "")

if [ -n "$ENTRY" ] && [ -n "$AUDIT_FILE" ]; then
  mkdir -p "$(dirname "$AUDIT_FILE")"
  echo "$ENTRY" >> "$AUDIT_FILE"
fi

exit 0
