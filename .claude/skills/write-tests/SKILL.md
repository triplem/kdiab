---
name: write-tests
description: Write a complete test suite for a story implementation following the test pyramid. Coverage must reach ≥ 80%. Use when implementation is ready and tests need to be written.
argument-hint: <story-id> <branch-name>
arguments: [story_id, branch]
disable-model-invocation: true
context: fork
agent: general-purpose
effort: high
allowed-tools: Read Write Edit Bash(git *) Bash(./gradlew *) Bash(npm *) Bash(dotnet *) Bash(find *) Bash(grep *)
---

## Story: $story_id | Branch: $branch

## Changed files on branch

!`git diff main...$branch --name-only 2>/dev/null || echo "Cannot diff — check branch name"`

## Instructions

### 1 — Read context

- Fetch story acceptance criteria from issue tracker
- Read all changed files listed above
- Identify new/modified classes, functions, and code paths
- Read `test-pyramid` rule

### 2 — Plan test layers

Map each acceptance criterion to a test layer:

| Criterion type | Layer |
|---|---|
| Business rule / domain logic | Unit |
| Service orchestration | Integration |
| API contract | Contract (Pact / Spring Cloud Contract) |
| User journey | Acceptance (BDD / Playwright) |

Write `docs/test-plan-$story_id.md` with this mapping.

### 3 — Write tests

See `test-patterns.md` for language-specific examples.

**Naming**: `should_<expected>_when_<condition>()`

For each public method: happy path + edge cases + error cases.
For each API endpoint: 2xx + 4xx + 5xx scenarios.
For each acceptance criterion: a BDD scenario.

### 4 — Coverage check and retry

```bash
# Run coverage
./gradlew test jacocoTestCoverageVerification   # Kotlin
npm run test:coverage                            # TypeScript
dotnet test --collect:"XPlat Code Coverage"     # .NET
```

If < 80%:
1. Identify uncovered lines
2. Write tests for them
3. Re-run coverage
4. Repeat up to 3 times
5. If still < 80% → flag to ImplementAgent: those lines may be dead code

### 5 — Challenge

`/challenge ImplementAgent "Test suite ready for $story_id — review for coverage gaps and missing edge cases"`
Incorporate feedback.

### 6 — Commit

```
test(<scope>): add test suite for $story_id

- Unit: N tests
- Integration: N tests
- Acceptance: N scenarios
- Coverage: N%

Refs #$story_id
```

## Output

- Test files on feature branch `$branch`
- `docs/test-plan-$story_id.md`
- Coverage ≥ 80%
