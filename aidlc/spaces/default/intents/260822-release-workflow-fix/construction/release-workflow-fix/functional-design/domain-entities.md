# Domain Entities — Release Workflow Fix (#1617)

Consumes `../../../inception/requirements-analysis/requirements.md`. (`unit-of-work.md`,
`application-design/components.md` etc. are **N/A — skipped**.)

## Not applicable

#1617 is a CI/CD workflow-configuration change (`backend-ci-reusable.yml`). It introduces **no domain
model, no persistent entity, and no data schema**. There is nothing to design at the domain layer.

The only "entities" involved are build-time GitHub Actions artifacts (already inventoried in
`business-logic-model.md`): the per-service `*-backend-image` (Docker image tarball) and
`*-backend-bom` (CycloneDX SBOM) workflow artifacts. These are transient CI artifacts, not domain
entities, and their only relevant property is their **name** (governed by `business-rules.md` R-1).
