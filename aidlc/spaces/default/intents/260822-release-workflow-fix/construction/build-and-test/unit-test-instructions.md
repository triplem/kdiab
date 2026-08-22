# Unit Test Instructions — Release Workflow Fix (#1617)

Consumes `../release-workflow-fix/code-generation/code-summary.md`.

## The "unit" under test

The only logic introduced is the shell prefix-strip `${SERVICE#kdiab-}`. Its unit behaviour is: given
`SERVICE=kdiab-<svc>`, output `<svc>`.

## How to verify

```bash
for s in measures profiles treatments calc carbs analyze nightscout users; do
  SERVICE="kdiab-$s"; echo "$SERVICE -> ${SERVICE#kdiab-}"   # expect: kdiab-$s -> $s
done
```

Equivalently, the derived name must equal each `release.yml` download name — verified in
`build-test-results.md` (16/16 match). No test framework applies (no application code). Per the Minimal
test strategy for a refactor, this requirement-driven check is the unit-level floor.
