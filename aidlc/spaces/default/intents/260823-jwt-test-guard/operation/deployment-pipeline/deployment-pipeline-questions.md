# Deployment Pipeline — Questions (#1588)

## No blocking questions

This is a single-guard security fix riding the established **publish-to-registry** pipeline
(`docker-publish.yml` → GHCR, `release.yml` semantic-release, `ci-common-publish.yml`). There is no
running production environment and no new CD infrastructure, so there is no genuine deployment-design
decision to make — nothing is asked rather than manufacturing a question.

The one operationally-significant item is documented (not a decision):

## NOTE (documented, not a question)
The change introduces one new optional env var, **`JWT_ALLOW_TEST_MODE`** (default `false`). It must
never be set in production and is required only where `JWT_TEST=true` is already used (CI/test). This is
captured in `rollback-runbook.md` § Operational NOTE and `cd-config.md`.

[Answer]: N/A — no open decisions; proceed.
