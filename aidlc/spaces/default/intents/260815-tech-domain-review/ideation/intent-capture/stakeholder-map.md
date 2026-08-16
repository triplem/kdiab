# Stakeholder Map — Technology & Domain Review

**Intent:** review technology and domain and suggest improvements
**Date:** 2026-08-15

> This is a solo-maintained project, so the stakeholder set is deliberately small. The maintainer holds
> every formal role; the other "stakeholders" are interest-holders whose needs anchor the review's
> priorities but who do not make decisions.

## Key Stakeholders & Interests

| Stakeholder | Role | Primary interest in this review |
|---|---|---|
| **Solo maintainer (you)** | Decision-maker + implementer + audience | A prioritized, evidence-linked plan for where to invest limited solo capacity; maintainability; sustainable pace |
| **T1D patients** (personas: sarah, mike) | Indirect / safety-critical | Clinical correctness and safety guardrails in dose calc, basal profiles, CGM/BGM interpretation — their health depends on it |
| **Prescribing doctors** (personas: dr_house, dr_cameron) | Indirect | Trustworthy data and correct doctor–patient access model; accurate analytics (AGP, HbA1c, timeline) |
| **Future collaborators** | Potential / non-imminent | Onboarding ease, code clarity, low duplication, documentation — a secondary lens (trigger is a health-check, not hiring) |
| **Data-protection / compliance interest** | Latent (no active auditor) | Safe handling of health data / PII; security posture — a stated *goal* (Q2 = D) even without an external auditor (Q3 ≠ D) |

## Decision-Makers vs. Influencers

- **Decision-maker:** the maintainer — sole authority on which recommendations are accepted, sequenced,
  and implemented.
- **Influencers (inform, do not decide):**
  - T1D clinical best practice / endocrinology domain knowledge — anchors the clinical-correctness and
    safety findings (Q2 = C, Q6 = A/B).
  - Security & health-data handling norms — anchors the security/compliance findings (Q2 = D).
  - The existing interop ecosystems (Nightscout, AAPS, xDrip+) — present as context, but interoperability
    is out of priority for this review (Q6 excludes D).

## Communication Requirements

- **Format:** review output is consumed asynchronously by the maintainer as **GitHub issues** (prioritized
  backlog), a **quick-wins list**, and a **phased roadmap** (Q8 = A, D, C).
- **Cadence:** none formal — no status meetings or external reporting. The artifacts are the communication.
- **Traceability:** because the audience may later include collaborators (and the maintainer's future
  self), every finding is written to stand alone — evidence-linked and self-explanatory rather than
  relying on tacit context.
