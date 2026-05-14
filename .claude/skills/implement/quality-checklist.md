# Quality Gate Checklist

Run `./.claude/scripts/quality-check.sh` or check each gate individually.

## Gates (all must pass before PR)

- [ ] `./gradlew test` / `npm test` / `dotnet test` — all pass
- [ ] Coverage ≥ 80% (`jacocoTestCoverageVerification` / `jest --coverage` / reportgenerator)
- [ ] Linting: `./gradlew detekt ktlintCheck` / `npm run lint && tsc --noEmit` / `dotnet format --verify-no-changes`
- [ ] SAST: `semgrep --config=auto --error --severity=ERROR src/`
- [ ] OpenAPI valid (if changed): `spectral lint openapi/openapi.yaml`
- [ ] No unlinked TODOs: `grep -rn "TODO\|FIXME" src/ | grep -v "#[0-9]"`
- [ ] Build succeeds: `./gradlew build -x test` / `npm run build` / `dotnet build -c Release`

## On gate failure

1. Read the failure output carefully
2. Fix the root cause
3. Re-run the specific gate
4. Retry up to 3 times
5. If still failing → label story `BLOCKED`, describe problem and options, notify human
