# GitHub MCP Integration

## Setup

```bash
# Install the GitHub MCP server
npm install -g @modelcontextprotocol/server-github

# Set token in environment
export GITHUB_TOKEN="ghp_your_token_here"
```

Required token scopes: `repo`, `issues`, `pull_requests`, `contents`

## Key Operations

### Issues (Epics & Stories)

```
# Create an issue
mcp__github__create_issue(
  owner: "org",
  repo: "repo",
  title: "[EPIC] Auth — JWT Authentication",
  body: "{{epic body}}",
  labels: ["epic", "pending-approval"],
  milestone: 1
)

# List issues for approval polling
mcp__github__list_issues(
  owner: "org",
  repo: "repo",
  labels: "epic,pending-approval",
  state: "open"
)

# Check for approval label
mcp__github__get_issue(owner: "org", repo: "repo", issue_number: 42)
# Check: issue.labels contains "approved"

# Add approved label
mcp__github__add_labels(
  owner: "org",
  repo: "repo",
  issue_number: 42,
  labels: ["approved"]
)

# Add comment (e.g., PR link, BLOCKED notice)
mcp__github__create_issue_comment(
  owner: "org",
  repo: "repo",
  issue_number: 42,
  body: "PR opened: https://github.com/org/repo/pull/55 — awaiting review."
)

# Close story after release
mcp__github__update_issue(
  owner: "org",
  repo: "repo",
  issue_number: 42,
  state: "closed",
  labels: ["released", "v1.2.0"]
)
```

### Pull Requests

```
# Create PR
mcp__github__create_pull_request(
  owner: "org",
  repo: "repo",
  title: "feat(auth): add JWT refresh token (#42)",
  body: "{{pr description from template}}",
  head: "feature/42-jwt-refresh",
  base: "main",
  labels: ["pending-review"]
)

# Check PR status (poll for approval)
mcp__github__get_pull_request(owner: "org", repo: "repo", pull_number: 55)
# Check: pr.state == "closed" && pr.merged == true
# OR: pr.reviews[].state == "APPROVED"

# Squash merge
mcp__github__merge_pull_request(
  owner: "org",
  repo: "repo",
  pull_number: 55,
  merge_method: "squash",
  commit_title: "feat(auth): add JWT refresh token (#42)",
  commit_message: "Closes #42"
)
```

### Releases

```
# Create release
mcp__github__create_release(
  owner: "org",
  repo: "repo",
  tag_name: "v1.2.0",
  target_commitish: "main",
  name: "Release v1.2.0",
  body: "{{changelog section}}",
  draft: false,
  prerelease: false
)
```

### Repositories

```
# List branches (check feature branch exists)
mcp__github__list_branches(owner: "org", repo: "repo")

# Get file contents (read existing config)
mcp__github__get_file_contents(
  owner: "org",
  repo: "repo",
  path: "openapi/openapi.yaml"
)
```

## Approval Detection Pattern

```python
# Poll every 5 minutes for approval
while True:
    issue = mcp__github__get_issue(owner, repo, issue_number)
    labels = [l.name for l in issue.labels]
    if "approved" in labels:
        log_approval_to_audit(issue_number, approved_by="human")
        break
    if "rejected" in labels:
        handle_rejection(issue_number)
        break
    sleep(300)  # 5 minutes
```

## Environment Variables

```bash
GITHUB_OWNER=your-org
GITHUB_REPO=your-repo
GITHUB_TOKEN=ghp_...
APPROVAL_LABEL=approved
REJECTION_LABEL=rejected
```
