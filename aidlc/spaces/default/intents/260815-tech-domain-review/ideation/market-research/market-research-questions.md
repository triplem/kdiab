# Market Research — Clarifying Questions

**Intent:** review technology and domain and suggest improvements
**Scope:** enterprise (comprehensive depth) — but market research is **tangential** for an internal,
recommendations-only review, so this is a deliberately **lean** set (3 questions).
**Mode:** _(set after mode choice)_

> Purpose here is narrow: position kdiab against the T1D ecosystem only insofar as it surfaces
> *improvement* ideas and *build-vs-buy* calls. If you'd rather skip market research entirely, pick
> **X. Other → "skip"** on Q1 and I'll produce a one-paragraph "not applicable" note and move on.

---

## Q1. Which T1D ecosystem products should I benchmark kdiab against? (select all that apply)
- A. Nightscout (self-hosted CGM / remote monitoring — kdiab already ships a compat layer)
- B. AndroidAPS / Loop / OpenAPS (automated insulin delivery / closed-loop)
- C. Tidepool (open data platform + clinic-facing tools)
- D. xDrip+ / Juggluco (CGM data-collection apps)
- X. Other (e.g. commercial apps — Dexcom Clarity, LibreView, mySugr, Glooko — or write "skip")

[Answer]: A (Nightscout), C (Tidepool), X → Nocturne (https://github.com/nightscout/nocturne — modern Nightscout successor)

## Q2. What is kdiab's intended positioning relative to those products?
- A. Personal self-hosted tool (for me / a small circle) — not competing with anyone
- B. An open-source alternative aiming to consolidate several of the above into one platform
- C. A clinician-facing analytics layer that complements existing CGM/pump apps
- D. Undecided — clarifying this is part of what the review should help with
- X. Other (please specify)

[Answer]: A (Personal self-hosted tool — not competing)

## Q3. Any component where you want an explicit build-vs-buy / keep-vs-adopt call? (select all that apply)
- A. Nightscout-compat layer (`kdiab-nightscout`) — keep building vs. defer to real Nightscout
- B. Dose calculator (`kdiab-calc`) — vs. established algorithms (AAPS/Loop/OpenAPS)
- C. CGM / data ingestion — vs. the xDrip+/Juggluco pipelines
- D. None — just want a general landscape comparison, no specific build-vs-buy call
- X. Other (e.g. analytics/AGP vs. Tidepool or commercial dashboards)

[Answer]: B (Dose calculator — kdiab-calc vs. AAPS/Loop/OpenAPS algorithms)
