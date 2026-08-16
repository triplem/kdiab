# Feasibility & Constraints — Clarifying Questions

**Intent:** review technology and domain and suggest improvements
**Scope:** enterprise (comprehensive depth); **Mode:** guided (answered inline 2026-08-15T19:39:32Z)

> Standard feasibility topics (integration, AWS accounts, org blockers) are largely N/A here: kdiab is a
> **self-hosted** Docker/Podman stack (no AWS), and this is a **solo, recommendations-only** review. These
> four questions target the feasibility factors that actually shape the improvement roadmap.

---

## Q1. Roughly how much capacity can you put toward implementing improvements later?
- A. A few hours a week (hobby pace)
- B. Steady part-time (regular evenings / weekends)
- C. Occasional focused bursts
- D. Unsure / varies
- X. Other (please specify)

[Answer]: C — occasional focused bursts

## Q2. What is the compliance / data-protection posture for the health data kdiab stores?
- A. Personal use only — no external users, informal
- B. GDPR-conscious (EU) but not formally certified
- C. Aiming toward formal compliance (GDPR / medical-device) eventually
- D. Not a concern right now
- X. Other (please specify)

[Answer]: C — aiming toward formal compliance (GDPR / medical-device) eventually

## Q3. Are there known pain points / known-broken areas to seed the risk log?
- A. Nothing specific — surfacing them is what the review is for
- B. Yes, I have specific areas in mind (I'll name them)
- C. A couple of vague suspicions but nothing concrete
- X. Other (please specify)

[Answer]: A — nothing specific; surface via the review

## Q4. Appetite for large changes? (Q7 said rewrites are on the table — how real is that for a solo maintainer?)
- A. Prefer incremental improvements; avoid big rewrites
- B. Open to targeted rewrites of specific components if clearly justified
- C. Genuinely open to anything, including large rewrites
- X. Other (please specify)

[Answer]: C — genuinely open to anything, including large rewrites
