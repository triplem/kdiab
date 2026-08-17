# Application Design — Questions

**Stage:** application-design (2.6) · **Phase:** Inception · **Depth:** Comprehensive (enterprise)
**Intent:** review technology and domain — recommendations-only (parks at end of Inception)

This stage designs the **review deliverable system**: the finding record schema, the `docs/review/`
document set, the GitHub issue/label taxonomy, and the findings→deliverables production pipeline.
Many deliverable decisions are already fixed upstream and are NOT re-asked here:

- Deliverables materialized as **both** `docs/review/` markdown **and** GitHub issues (FR-D.1) — fixed
- Priority ordering by **value-density, clinical-safety strictly first** (NFR-3) — fixed
- 100% evidence-linkage; no finding without a citation (NFR-1) — fixed
- `gh`-unavailable fallback: docs ship, issues queue as follow-up (FR-D.1, A-2) — fixed
- Already-tracked items are cross-referenced, never re-filed (FR-D.5) — fixed

Each question uses the `[Answer]:` tag. Options A–E where applicable; **X. Other** always available.
A recommended default is noted per question, but the choice is yours — nothing is pre-answered.

---

## Q1 — `docs/review/` document-set structure

How should the review documents under `docs/review/` be laid out?

- **A.** Per-theme finding docs (`clinical-safety.md`, `data-model.md`, `security.md`, `tech-debt.md`,
  `modernization.md`) + three cross-cutting docs (`BACKLOG.md`, `QUICK-WINS.md`, `ROADMAP.md`) + a
  `README.md` index. *(Recommended — matches the 4 themes + 3 deliverables split; each theme doc is
  independently reviewable.)*
- **B.** One consolidated `REVIEW.md` (all findings inline by theme) + `BACKLOG.md` + `QUICK-WINS.md` +
  `ROADMAP.md`.
- **C.** `BACKLOG.md` as the single master (every finding inline) + `QUICK-WINS.md` + `ROADMAP.md` only
  — no separate per-theme docs.
- **D.** One file per finding (many small files) plus the three cross-cutting index docs.
- **X.** Other (please specify)

[Answer]: A — Per-theme finding docs (clinical-safety, data-model, security, tech-debt, modernization) + BACKLOG.md + QUICK-WINS.md + ROADMAP.md + README index. *(Mode: guided, 2026-08-16)*

---

## Q2 — Finding record schema (optional fields)

The schema will **always** carry the mandated fields: `id`, `area`, `severity`, `evidence-link`,
`recommendation`, **patient-safety impact** (FR-1.3), **incremental alternative** for any rewrite
(C-1), and **cross-reference** to an existing issue/ADR when the item is already tracked (FR-D.5).
Which *optional* fields should be added on top? (select all that apply)

- **A.** Effort estimate (T-shirt S/M/L). *(Recommended)*
- **B.** Roadmap phase tag (near/mid/long) stamped on each item. *(Recommended)*
- **C.** Confidence / uncertainty flag on the finding (how sure the review is).
- **D.** None — mandated fields only.
- **X.** Other (please specify)

[Answer]: A, B, C — add Effort estimate (S/M/L), Roadmap phase tag, and a Confidence/uncertainty flag on top of the mandated fields. *(Mode: guided, 2026-08-16)*

---

## Q3 — Severity scale

What severity scale should findings use?

- **A.** 4-tier **Critical / High / Medium / Low**, with *Critical reserved for patient-safety*
  findings (clinical correctness, dosing, guardrails). *(Recommended — keeps a clear safety-first band.)*
- **B.** 3-tier **High / Medium / Low**.
- **C.** 5-tier with a dedicated **Safety** band above Critical (Safety / Critical / High / Medium / Low).
- **X.** Other (please specify)

[Answer]: A — 4-tier Critical / High / Medium / Low, Critical reserved for patient-safety findings. *(Mode: guided, 2026-08-16)*

---

## Q4 — GitHub issue structure

How should the findings map onto GitHub issues?

- **A.** One **epic** issue ("Tech & Domain Review") + one **native sub-issue per backlog item**
  (using the repo's `addSubIssue` GraphQL pattern), each labelled `area:*` + `severity:*`.
  *(Recommended — matches the repo's sub-issue rule and gives a progress tracker on the epic.)*
- **B.** Flat issues, **one per backlog item**, labelled `area:*` + `severity:*`, no epic.
- **C.** One issue **per theme** (5 issues) aggregating that theme's findings.
- **X.** Other (please specify)

[Answer]: A — One epic "Tech & Domain Review" + native sub-issue per backlog item (addSubIssue), each labelled area:* + severity:*. *(Mode: guided, 2026-08-16)*

---

## Q5 — Label taxonomy & creation policy

What label set should the GitHub issues use, and how are missing labels handled?

- **A.** Create a **dedicated review label set**: `area:clinical-safety`, `area:data-model`,
  `area:security`, `area:tech-debt`, `area:modernization`; `severity:critical|high|medium|low`;
  plus `quick-win` and `review`. Create any that don't exist. *(Recommended.)*
- **B.** **Reuse existing repo labels** wherever they already fit; create only the genuinely missing ones.
- **C.** Minimal labels only — `review` + `severity:*`, no `area:*`.
- **X.** Other (please specify)

[Answer]: B — Reuse existing repo labels wherever they already fit; create only the genuinely missing ones (in practice the `area:*` labels won't pre-exist, so they get created; a reuse-first, create-the-rest policy). *(Mode: guided, 2026-08-16)*

---

## Q6 — Effort estimate scale (for items and roadmap phases)

What effort scale should back the estimates?

- **A.** **T-shirt S/M/L** where S ≈ one maintainer burst (~1 day), M ≈ 2–3 days, L ≈ multi-burst (~5 days).
  *(Recommended — matches the team's confirmed S/M/L estimate convention.)*
- **B.** Hour/day ranges per item.
- **C.** No per-item effort; effort noted only per roadmap phase.
- **X.** Other (please specify)

[Answer]: A — T-shirt S/M/L (S ≈ ~1-day burst, M ≈ 2–3 days, L ≈ multi-burst ~5 days). *(Mode: guided, 2026-08-16)*

---

## Q7 — Roadmap phase model

How should the phased roadmap (FR-D.3) be structured?

- **A.** **Value-density bands:** Near = quick-wins + Must clinical-safety; Mid = Should
  security + tech-debt; Long = Could modernization. Each item independently shippable in a burst.
  *(Recommended — mirrors the requirements' priority ordering and burst-capacity NFR.)*
- **B.** By theme (all of one theme, then the next).
- **C.** By ascending effort (all S, then M, then L).
- **X.** Other (please specify)

[Answer]: A — Value-density bands: Near = quick-wins + Must clinical-safety; Mid = Should security + tech-debt; Long = Could modernization. *(Mode: guided, 2026-08-16)*

---

## Q8 — Evidence-link format (traceability durability)

What form should each evidence citation take? (NFR-1 requires every finding to be evidence-linked.)

- **A.** `path/to/File.kt:Lnn` **pinned to the RE commit** (`d6c8866b`) so line references stay valid
  even as `main` moves. *(Recommended — durable + precise.)*
- **B.** `path/to/File.kt` + **symbol/function name** (no line number — more stable across edits).
- **C.** File path only.
- **X.** Other (please specify)

[Answer]: B — `path/to/File.kt` + symbol/function name (no line number — more stable across edits). Prose may add line context, but the canonical citation is path + symbol. *(Mode: guided, 2026-08-16)*
