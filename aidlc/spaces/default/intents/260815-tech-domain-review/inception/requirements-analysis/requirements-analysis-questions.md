# Requirements Analysis — Clarifying Questions

Intent: **review technology and domain and suggest improvements** (assessment → prioritized recommendations).
Ideation resolved Q1–Q9; these target the remaining gaps that shape the review's deliverables and acceptance criteria.

---

## Q1. Deliverable materialization & persistence — how should the prioritized backlog + quick-wins + roadmap be delivered?

- A. Author them as **markdown documents committed to the repo** (e.g. `docs/review/`), for you to triage into issues yourself
- B. **Create actual GitHub issues** (labelled by area + severity, evidence-linked) **and** a summary roadmap doc in the repo
- C. **Both**: repo docs now *and* I open the GitHub issues in this run
- D. Keep everything in the **intent record dir only** (`aidlc/spaces/default/intents/.../inception/`), nothing in `docs/` or GitHub
- X. Other (please specify)

[Answer]: C — Both: repo docs now AND open the GitHub issues in this run (labelled by area + severity, evidence-linked).

---

## Q2. Clinical-safety review depth — for the non-negotiable `kdiab-calc` dose-correctness review, how deep should findings go?

- A. **Flag concerns** with code evidence + reference pointers only (lightweight)
- B. **Concrete**: corrected formulas, worked dose examples, and proposed test cases for each concern
- C. **Deep**: B plus a gap analysis against a named clinical reference model (IOB/insulin-on-board, ISF/correction factor, carb ratio, DIA)
- X. Other (please specify)

[Answer]: A — Flag concerns with code evidence + reference pointers only (lightweight).

---

## Q3. Park point — the intent recommends parking at end of Inception (recommendations delivered) rather than running Construction/Operation as busywork. Confirm?

- A. **Park at end of Inception** — deliver recommendations; resume Construction later only if you choose to implement
- B. **Continue into Construction** in this run to implement selected quick wins
- X. Other (please specify)

[Answer]: A — Park at end of Inception; deliver recommendations. Resume Construction later only if implementation is chosen.
