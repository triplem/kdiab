# Log Queries — Review Deliverable Observability

> Stage 4.4. The "logs" for a docs deliverable are its git history, the GitHub issue tracker, and the
> Actions run log. These are the copy-paste queries behind the dashboards/alarms. No log-aggregation
> backend (Loki/CloudWatch Logs) — the sources are git + `gh`, run from the repo root.

## Progress / burn-down (D1)

```bash
# Open vs closed review findings (the epic burn-down)
gh issue list -R triplem/kdiab --label review --state open   --limit 200 --json number | grep -c '"number"'
gh issue list -R triplem/kdiab --label review --state closed --limit 200 --json number | grep -c '"number"'

# Quick-win completion
gh issue list -R triplem/kdiab --label quick-win --state closed --json number,title

# Per-area open counts
for a in clinical-safety data-model security tech-debt modernization; do
  n=$(gh issue list -R triplem/kdiab --label "area:$a" --state open --json number | grep -c '"number"')
  echo "area:$a open=$n"
done
```

## Integrity history (D2)

```bash
# review-verify gate run history (green/red over time)
gh run list -R triplem/kdiab --workflow review-verify.yml --limit 20

# most recent gate conclusion
gh run list -R triplem/kdiab --workflow review-verify.yml --limit 1 --json conclusion,createdAt
```

## Currency / staleness (D3, A2)

```bash
# Which cited files changed since the codekb baseline (drives the monitor)
python3 docs/review/monitor.py            # full report (fresh/changed/missing + burn-down)

# Ad-hoc: has one cited file changed since baseline?
git log --oneline d6c8866b..HEAD -- kdiab-calc/src/main/kotlin/org/javafreedom/kdiab/calc/application/service/DoseCalculationService.kt

# Manual scheduled monitor run (also upserts the drift issue if any)
gh workflow run review-monitor.yml -R triplem/kdiab
```

## Deliverable-change audit

```bash
# Everything that touched the deliverable
git log --oneline -- docs/review/

# Who/what changed a specific finding's theme doc
git log --follow --oneline -- docs/review/clinical-safety.md
```

## Notes

- All queries are read-only except `gh workflow run` (triggers the advisory monitor) and the monitor's
  own idempotent drift-issue upsert.
- The `grep -c '"number"'` counting trick avoids requiring `jq` (not guaranteed on every host); `gh`'s
  `--json` output is one `"number"` per issue.
