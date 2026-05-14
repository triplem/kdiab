#!/usr/bin/env bash
# Setup MCP servers for this project.
# Run once after cloning: ./.claude/scripts/setup-mcp.sh [github|gitlab|jira|all]

set -euo pipefail

TRACKER="${1:-github}"

echo "Installing MCP server dependencies..."
npm install -g @modelcontextprotocol/server-filesystem

case "$TRACKER" in
  github|all)
    echo "Installing GitHub MCP server..."
    npm install -g @modelcontextprotocol/server-github
    echo "Set GITHUB_TOKEN in your environment."
    ;;
esac

case "$TRACKER" in
  gitlab|all)
    echo "Installing GitLab MCP server..."
    npm install -g @modelcontextprotocol/server-gitlab
    echo "Set GITLAB_TOKEN and GITLAB_API_URL in your environment."
    ;;
esac

case "$TRACKER" in
  jira|all)
    echo "Installing Jira MCP server..."
    npm install -g @modelcontextprotocol/server-jira
    echo "Set JIRA_URL, JIRA_USER, JIRA_TOKEN in your environment."
    ;;
esac

echo ""
echo "Copy .env.example to .env and fill in the values:"
cat .env.example 2>/dev/null || echo "(no .env.example found — create one)"
echo ""
echo "Done. Start Claude Code in this directory and MCP servers will be available."
