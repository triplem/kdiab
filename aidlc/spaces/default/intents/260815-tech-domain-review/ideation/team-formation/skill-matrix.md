# Skill Matrix — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Date:** 2026-08-15

> Solo maintainer. The "matrix" records where the one person is strong vs. where the review should lean on
> external references / AI-DLC agent personas — especially the **clinical (T1D) domain**, the one area a
> software maintainer typically cannot self-validate.

## Maintainer skill profile (inferred from the codebase; verify)

| Skill area | Evident strength | Note |
|---|---|---|
| Kotlin / Ktor backend | Strong | 9 well-structured hexagonal services, CI, Detekt/Kover |
| React / TypeScript frontend | Strong | React 19 SPA, strict TS |
| PostgreSQL / Exposed / Liquibase | Strong | Per-service schemas, migrations |
| DevOps (Docker/Podman, GitHub Actions, OTEL) | Strong | Full self-hosted stack + observability |
| **T1D clinical domain** | **Gap risk** | The one area software skill does not cover — dosing correctness needs clinical grounding |

## Coverage for this review

Because there is no team, the review's "roles" are filled by **AI-DLC agent personas** + external reference:

| Review theme | Covered by | External reference needed |
|---|---|---|
| Clinical safety (Theme 1) | architect + developer personas + **T1D domain review** | ⚠️ **Yes** — AAPS/Loop/OpenAPS docs as dosing oracle; consider a clinical advisor before claiming validity |
| Security & compliance | devsecops + compliance personas | GDPR / MDR references |
| Code health | quality + developer personas | detekt/Kover output (self-serve) |
| Modernization / arch | architect + aws-platform (N/A cloud) | library changelogs |

## Key gap → recommendation

The **clinical-domain gap** is the most important staffing note: a solo software maintainer can verify that
`kdiab-calc` *computes what it says*, but not that the *clinical logic is safe for real therapy*. The review
should therefore (a) validate against published algorithms as oracles, and (b) flag that **independent
clinical review** is advisable before `kdiab-calc` is ever positioned as more than advisory. The project
already ships `/doctor-t1d-review` and `/patient-t1d-review` skills — use them.
