# Quality Gate Checklist

Run `./.claude/scripts/quality-check.sh` or check each gate individually.

## Gates (all must pass before PR)

- [ ] `./gradlew check` — tests + **Kover** coverage ≥ 80% + detekt (Kotlin); `npm test` / `dotnet test` for other stacks
- [ ] Linting: `./gradlew detekt` / `npm run lint && tsc --noEmit` / `dotnet format --verify-no-changes`
- [ ] `npm run build` — must complete with **zero TypeScript errors** (strict `noUnusedLocals` causes TS6196 failures that break Docker builds)
- [ ] SAST: `semgrep --config=auto --error --severity=ERROR src/`
- [ ] OpenAPI valid (if changed): `spectral lint openapi/openapi.yaml`
- [ ] No unlinked TODOs: `grep -rn "TODO\|FIXME" src/ | grep -v "#[0-9]"`
- [ ] Build succeeds: `./gradlew build -x test` / `npm run build` / `dotnet build -c Release`
- [ ] Publish works (if `kdiab-common/build.gradle.kts` changed): `cd kdiab-common && ./gradlew publishToMavenLocal`

## On gate failure

1. Read the failure output carefully
2. Fix the root cause
3. Re-run the specific gate
4. Retry up to 3 times
5. If still failing → label story `BLOCKED`, describe problem and options, notify human

## kdiab-specific notes

- **Coverage tool is Kover, not JaCoCo** — `./gradlew check` runs `koverVerify` automatically; never use `jacocoTestCoverageVerification`
- **`npm run build` is mandatory** for any UI change — TypeScript strict mode catches unused symbols at build time, not just at type-check time
- **Publish gate** — `docker-publish.yml` calls `./gradlew publish` on every main push; a broken publish config passes all other gates but breaks the CI release job
