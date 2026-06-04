---
name: release
description: Squash-merge an approved PR, bump the semantic version, generate a changelog, publish the artefact, and close the story.
argument-hint: <pr-id>
arguments: pr_id
disable-model-invocation: true
allowed-tools: Bash(git *) Bash(gh *) Bash(glab *) Bash(./gradlew *) Bash(npm *) Bash(dotnet *) Write
hooks:
  PreToolUse:
    - matcher: "Bash"
      if: "Bash(git push *)"
      hooks:
        - type: command
          command: "${CLAUDE_PROJECT_DIR}/.claude/hooks/pre-push-gates.sh"
          timeout: 120
          statusMessage: "Running quality gates before push..."
---

## Recent commits since last tag

!`git log $(git describe --tags --abbrev=0 2>/dev/null || echo "HEAD~20")..HEAD --oneline 2>/dev/null | head -30`

## Instructions

### 1 — Squash merge

```bash
gh pr merge $pr_id --squash --delete-branch     # GitHub
glab mr merge $pr_id --squash --remove-source-branch  # GitLab
```

Squash commit message: `<type>(<scope>): <story title> (#<story-id>)`

### 2 — Version bump

Scan commits since last tag. Determine bump:
- Any `feat:` → minor (0.X.0)
- Any `fix:` or `perf:` → patch (0.0.X)
- Any `BREAKING CHANGE:` or `!` type → major (X.0.0)
- Only `chore:`/`docs:`/`test:` → no release (skip)

Apply bump:
```bash
# Kotlin/Gradle — update gradle.properties: version=X.Y.Z
# Node — npm version X.Y.Z --no-git-tag-version
# .NET — update <Version> in .csproj

git tag -a "vX.Y.Z" -m "chore(release): vX.Y.Z"
git push origin main --tags
```

### 3 — Changelog

Prepend new section to `CHANGELOG.md` following `templates/changelog.md` format.

```bash
npx conventional-changelog-cli -p angular -i CHANGELOG.md -s  # Node
./gradlew generateChangelog  # Kotlin with git-changelog-gradle-plugin
```

### 4 — Build and publish

```bash
# Kotlin/JVM
./gradlew build publish   # Maven/Nexus/GitHub Packages
./gradlew jib             # Docker image

# TypeScript
npm ci && npm run build && npm publish --access public

# .NET
dotnet publish -c Release
dotnet nuget push *.nupkg --source $NUGET_SOURCE
```

### 5 — Create tracker release

```
mcp__github__create_release(tag_name:"vX.Y.Z", name:"Release vX.Y.Z", body:"<changelog section>")
```

### 6 — Close story

```
mcp__github__update_issue(state:"closed", labels:["released","vX.Y.Z"])
```

## Output

- Squash commit on `main` + git tag
- Updated `CHANGELOG.md`
- Published artefact
- GitHub/GitLab release entry
- Story closed (`released`, `vX.Y.Z` labels)
