# Build Instructions — Release Workflow Fix (#1617)

Consumes `../release-workflow-fix/code-generation/code-summary.md`.

## There is no application build

The change is a GitHub Actions workflow file (`backend-ci-reusable.yml`). Nothing compiles; there is no
artifact to build for this change. The reusable workflow is "built" (rendered + executed) by GitHub
when a backend CI calls it.

## How to validate the workflow locally

```bash
# YAML validity
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/backend-ci-reusable.yml'))"
# Inspect the rendered step structure
yq '.jobs.build.steps[] | select(.name=="Derive short service name" or (.name|test("Upload")))' \
   .github/workflows/backend-ci-reusable.yml
# Static lint (when available — not installed in this env; runs in CI)
actionlint .github/workflows/backend-ci-reusable.yml
```
