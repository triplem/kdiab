# Requirements: Patient User Guide with Playwright Screenshots

**Project:** kdiab — Patient-Facing Documentation  
**Version:** 1.1  
**Status:** DRAFT  
**Date:** 2026-06-02  
**Author:** RequirementsAgent

---

## 1. Problem Statement

There is no end-user documentation for kdiab patients. A patient who registers for the first time has no guidance on:

- What each screen shows and what actions are available
- How to interpret clinical metrics (HbA1c estimate, AGP, TIR)
- How to use the dose calculator safely
- How to configure their profile and alarm thresholds
- How external devices (AAPS, xDrip+, Juggluco via Nightscout) connect

Without documentation, patient onboarding depends entirely on verbal instruction from the treating physician, and clinical misinterpretation of the dose calculator carries patient-safety risk.

---

## 2. Stakeholders

| Role | Who |
|---|---|
| Primary readers | T1D patients using kdiab independently |
| Secondary readers | Treating endocrinologists walking through the app with patients |
| Author / maintainer | triplem |
| Clinical safety reviewer | T1D specialist (per `/doctor-t1d-review`) |

---

## 3. Functional Requirements

### 3.1 Document structure

| ID | Requirement | Priority | Acceptance test |
|---|---|---|---|
| FR-001 | Guide is structured in four parts: (1) Getting Started, (2) Daily Workflow, (3) Analytics & Reports, (4) Settings & Profiles + Integrations | Must | Section headings are present and match this structure |
| FR-002 | Each section begins with a one-paragraph "what this section covers" summary | Should | Reviewer confirms every section has a summary paragraph |
| FR-003 | A glossary of T1D terms (CGM, BGM, IOB, COB, ISF, ICR, DIA, TIR, AGP, HbA1c, basal, bolus, SEA, CGP, PGR) is included at the end | Must | All abbreviations used in the guide appear in the glossary |

### 3.2 Part 1 — Getting Started

| ID | Requirement | Priority | Acceptance test |
|---|---|---|---|
| FR-010 | Guide covers the login flow: navigating to the app, clicking "Log in", entering credentials in Keycloak, returning to the dashboard | Must | Step-by-step procedure present with screenshot of the login page and one of the post-login dashboard |
| FR-011 | Guide covers the "Create an account" flow at a high level (registration is handled by invitation from a doctor or admin) | Should | Procedure notes that self-registration requires an invitation; no full step-by-step needed |
| FR-012 | Guide explains the navigation bar (Dashboard, Measures, Treatments, Profiles, Timeline, Analytics, Report, Food DB, Dose Calc, Settings) | Must | Screenshot of navigation bar annotated with label callouts |
| FR-013 | Guide explains the Patient Banner shown when a doctor is viewing a patient's data | Should | Banner screenshot and explanation of what "Viewing patient:" means |

### 3.3 Part 2 — Daily Workflow

| ID | Requirement | Priority | Acceptance test |
|---|---|---|---|
| FR-020 | Dashboard section explains: glucose hero tile (current reading, delta, minutes ago), trend arrow, IOB/COB summary, basal current rate, device status widget (sensor age, reservoir, battery) | Must | Each element is labelled and explained in the text; screenshot with callout annotations |
| FR-021 | Dashboard section explains the CGM trend chart and how to navigate time windows (Earlier / Later) | Must | Screenshot of chart with example; navigation procedure present |
| FR-022 | Dashboard section explains the basal rate chart below the CGM chart | Should | Screenshot and explanation of scheduled vs. delivered basal |
| FR-023 | Measures section explains how to add a manual BGM reading (Add Measure modal: date/time, glucose value, source) | Must | Screenshot of Add Measure modal; step-by-step procedure |
| FR-024 | Measures section explains the measures list: filtering, sorting, what each column means | Should | Screenshot of list; column descriptions present |
| FR-025 | Treatments section explains how to log a meal bolus (dose + carbs), a correction bolus, a basal change, and a site/sensor change | Must | Screenshot of Add Treatment modal with each type; one procedure per treatment type |
| FR-026 | Treatments section explains how to delete a treatment (doctor/admin only) and what happens if a patient tries | Should | Note in the guide that deletion requires DOCTOR or ADMIN role |
| FR-027 | Dose Calculator section explains the full workflow: enter current BG → select CGM trend → enter carbs → review recommended dose → adjust → Accept & Log | Must | Step-by-step procedure with screenshot at each step; disclaimer text from i18n key `doseCalc.disclaimer` reproduced verbatim |
| FR-028 | Dose Calculator section includes a prominent clinical safety note: "This is a suggested dose — always verify with your healthcare team before injecting." | Must | Safety note is visually distinct (AsciiDoc `WARNING` admonition block) and not merely a footnote |
| FR-029 | Dose Calculator section explains the SEA (Spritz-Ess-Abstand) field and what the timer means | Should | SEA concept explained; screenshot of timer |
| FR-030 | Food Database section explains how to search for food items and add a carb entry | Must | Screenshot of Food DB search; step-by-step for logging carbs |
| FR-031 | Dashboard section explains the Quick Log buttons (one-tap logging of insulin change, site change, sensor insert without opening a modal); position on screen and what each icon means | Must | Screenshot of quick log row; table of event types with descriptions |

### 3.4 Part 3 — Analytics & Reports

| ID | Requirement | Priority | Acceptance test |
|---|---|---|---|
| FR-040 | Analytics section explains the HbA1c estimate card: formula (DCCT), what the colour bands mean, ADA target of <7%, and the disclaimer that it is not a lab test | Must | Card screenshot; formula source cited; disclaimer in AsciiDoc `IMPORTANT` admonition |
| FR-041 | Analytics section explains Time In Range (TIR): the five bands (Very Low, Below, Target, Above, Very High), colour coding, and the clinical target of ≥70% in range | Must | TIR bar screenshot; table of bands with thresholds (in both mg/dL and mmol/L) |
| FR-042 | Analytics section explains the AGP (Ambulatory Glucose Profile) chart: what the percentile bands (10th, 25th, 75th, 90th) and median line mean; how to interpret it with a treating physician | Must | AGP chart screenshot; percentile band explanation; note to review with physician |
| FR-043 | Analytics section explains the Basal Rate Average and Bolus Average hourly charts | Should | Chart screenshots; brief explanation of what each shows |
| FR-044 | Analytics section explains the date-range selector (timeframe picker) and how to change the analysis period | Must | Screenshot of timeframe selector |
| FR-045 | Timeline section explains the combined view: which events appear, how to read the overlaid data | Should | Screenshot of timeline; legend explained |
| FR-046 | Report section explains the date-range controls: preset buttons (Last 7 / 14 / 30 / 90 days), custom From/To date entry, the advisory that AGP requires ≥14 days, and the warning shown for large ranges | Must | Screenshot of date-range controls; all presets listed |
| FR-047 | Report section explains the Page Selection panel: which pages are always included (Summary Report, Summary) vs. user-toggleable; Select All / Deselect All; count of deselected pages shown | Must | Screenshot of page selection panel; list of all 11 pages with their display names |
| FR-048 | Report section explains the Generate Report button and the Download PDF button; notes that generation may take time for large date ranges | Must | Step-by-step: set range → select pages → Generate → Download PDF |
| FR-049 | Report section documents each of the 11 report sub-pages with a one-paragraph description of what it shows and when it is clinically useful: | Must | Each sub-page described; screenshots of representative pages |
|  | • **Summary Report** (AUSWERTUNG) — patient info, days analysed, CGM readings, insulin types, reservoir/site/sensor change counts, TIR (profile and standard thresholds), glucose statistics (min/max/mean/SD), daily averages (carbs, bolus, basal, total insulin) | | |
|  | • **Summary** — compact TIR + glucose stats overview | | |
|  | • **AGP Percentile Chart** — AGP with sensor wear days and TIR summary (same as Analytics AGP but in report layout) | | |
|  | • **Daily Statistics** (DAILY_STATS) — per-day table: readings count, TIR bands (%), median, P25/P75, eHbA1c | | |
|  | • **Daily Trend** (DAILY_TREND) — hour × day heat-map coloured by glucose zone; carbs indicators | | |
|  | • **Weekly Overlay Chart** (WOCHENGRAPHIK) — all days' glucose curves overlaid on a 24-hour axis, coloured by calendar week | | |
|  | • **Daily Charts** (DAILY_CHARTS) — one CGM trace per day with bolus/carb/event markers; capped at most recent 14 days | | |
|  | • **Glucose Distribution** — histogram of CGM readings by range; zone percentages | | |
|  | • **Basal Profile** — active profile's basal schedule, ICR, ISF, targets | | |
|  | • **Comprehensive Glucose Pentagon (CGP)** — pentagon radar comparing patient vs. reference on 5 axes: ToR, VarK, Hypo Intensity, Hyper Intensity, Mean Glucose; PGR score; citation: Vigersky et al. (2018) | | |
|  | • **Basal Rate Chart** (BASAL_RATE) — 24-hour basal schedule as table + bar chart; total daily basal | | |
| FR-050-report | Report section includes a NOTE admonition: the report is generated server-side and reflects data at the moment of generation; re-generate to include new entries | Should | Admonition present |

### 3.5 Part 4 — Settings, Profiles & Integrations

| ID | Requirement | Priority | Acceptance test |
|---|---|---|---|
| FR-070 | Settings section covers every field: timezone, language, time format, glucose unit, weight unit, sensor lifespan, alarm thresholds (urgent high/high/low/urgent low) | Must | All eight settings fields described; screenshot of Settings form |
| FR-071 | Settings section explicitly documents that glucose unit and timezone changes require a re-login to take effect (i18n hint `settings.jwtBackedHint`) | Must | Re-login requirement in an `IMPORTANT` admonition |
| FR-072 | Settings section explains alarm threshold ordering rule (urgent low < low < high < urgent high) | Must | Rule stated; validation error screenshot included |
| FR-073 | Profiles section explains what a basal rate profile is, how to read the time-segmented schedule, and how to activate a profile | Must | Screenshot of profile list and editor; activation procedure |
| FR-074 | Profiles section explains profile history and the copy-on-write model (editing creates a new version; old versions are archived) | Should | Profile history screenshot; copy-on-write concept explained in plain language |
| FR-075 | Profiles section explains the Proposed badge (a doctor has proposed a profile change) and how to review a diff | Should | ProposedBadge screenshot; diff view screenshot |
| FR-076 | Integrations section explains how the Nightscout API v1 compatibility endpoint works, which devices can connect (AAPS, xDrip+, Juggluco), and what data flows in each direction | Must | Architecture note; device list; Nightscout base URL format (`http(s)://<host>/api/v1`) and required API token field documented; both device→kdiab and kdiab→device data flows described |
| FR-077 | Integrations section states that Nightscout integration is optional and must be enabled by an admin | Should | Note present |

### 3.6 Playwright screenshot script

| ID | Requirement | Priority | Acceptance test |
|---|---|---|---|
| FR-080 | A Playwright spec `kdiab-ui/e2e/screenshots.spec.ts` captures all screenshots referenced in the guide | Must | Script runs to completion against the podman compose stack at `http://localhost:3005` without failures |
| FR-081 | Screenshots are saved to `docs/images/patient-guide/` with descriptive kebab-case filenames (e.g. `dashboard-overview.png`, `agp-chart.png`) | Must | All files present after script run; filenames match `image::` references in the `.adoc` |
| FR-082 | Script authenticates as `sarah` (password: `password`) using the existing `auth.setup.ts` pattern and reuses the `e2e/.auth/sarah.json` storage state | Must | Script does not prompt for credentials; session reuse confirmed in test output |
| FR-083 | Script waits for network idle before capturing each screenshot to avoid partial renders | Must | No screenshots show loading spinners |
| FR-084 | Script captures screenshots at 1280×900 (desktop) viewport | Must | Image dimensions confirmed at 1280×900 |
| FR-085 | Script is run in two steps: (1) `npx playwright test e2e/auth.setup.ts` to create the session file, then (2) `npx playwright test e2e/screenshots.spec.ts --project=chromium`; both commands run from `kdiab-ui/`; requires the podman compose stack to be up | Must | Both commands documented in guide; step 2 fails with a clear error if step 1 has not been run |
| FR-086 | Screenshots are committed to git in `docs/images/patient-guide/` as part of the same PR as the guide; re-capture and re-commit when significant UI changes occur; screenshots are **not** generated in CI | Must | Images present in the git history of the PR; no CI step attempts to regenerate them |

### 3.7 Output artefacts

| ID | Requirement | Priority | Acceptance test |
|---|---|---|---|
| FR-090 | `docs/patient-guide.adoc` — the AsciiDoc guide, following the existing `docs/` style (`:toc: left`, `:icons: font`, level-1 parts, level-2 chapters, level-3 sections) | Must | File present; Gradle `asciidoctor` task produces valid HTML |
| FR-091 | `kdiab-ui/e2e/screenshots.spec.ts` — the Playwright screenshot script | Must | Script file present; runs without error |
| FR-092 | `docs/images/patient-guide/` — directory containing all PNG screenshots committed to git | Must | Directory present; all referenced images resolvable by the asciidoctor build |
| FR-093 | Guide is linked from `docs/index.adoc` navigation | Should | `index.adoc` includes a link to `patient-guide.adoc` |

---

## 4. Non-Functional Requirements

| ID | Category | Requirement | Measurement |
|---|---|---|---|
| NFR-001 | Clinical safety | Dose calculator disclaimer reproduced verbatim; rendered in a WARNING admonition | Manual review confirms text matches `doseCalc.disclaimer` i18n key |
| NFR-002 | Clinical safety | HbA1c estimate disclaimer present; renders in an IMPORTANT admonition | Manual review |
| NFR-003 | Accessibility | AsciiDoc images include `alt=` text on every screenshot | `grep -c 'image::.*\[' docs/patient-guide.adoc` equals number of `alt=` attributes |
| NFR-004 | Completeness | Every abbreviation used in the body text appears in the glossary | Automated grep check in CI |
| NFR-005 | Freshness | When significant UI changes are made, the developer re-runs the screenshot script locally and commits updated images in the same PR as the code change; no CI regeneration | PR checklist item: "screenshots re-captured if UI changed" |
| NFR-006 | Language | All UI label names in the guide match the English i18n strings in `src/i18n/locales/en.json` | Reviewer cross-checks against i18n keys |
| NFR-007 | Clinical safety | Patient guide must receive an ACCEPT verdict from `/doctor-t1d-review` before the PR merging the guide to `main` is opened; verdict must be logged to `audit/agent-log.jsonl` | `audit/agent-log.jsonl` contains an entry with `"action":"challenge"`, `"agent":"doctor-t1d-review"`, `"verdict":"ACCEPT"` referencing `docs/patient-guide.adoc` |

---

## 5. Out of Scope (v1)

- German translation of the guide (future milestone)
- Doctor-facing documentation (doctor workflow, patient management, invitations)
- Admin documentation (user management, doctor assignment)
- In-app onboarding tour / tooltips (separate story)
- Video walkthroughs
- Mobile / responsive screenshots (desktop 1280×900 only)

---

## 6. Assumptions

| ID | Assumption | Risk if wrong |
|---|---|---|
| A-001 | The podman compose stack with seeded sarah/mike data is the canonical screenshot environment | Screenshots may show different data if a different user or environment is used |
| A-002 | `auth.setup.ts` storage state reuse is sufficient for the screenshot script; no re-authentication is needed mid-script | Some screenshots may fail if session expires during a long run |
| A-003 | The Nightscout section does not require live device data; it is documented from the spec/code only | Section may be incomplete if device-specific behaviour is not fully covered by the OpenAPI spec |
| A-004 | `docs/images/patient-guide/` PNG files are committed to git and captured by running the Playwright script manually on a local podman compose stack; they are not generated in CI | Screenshots become stale if a developer forgets to re-capture after a UI change; mitigated by NFR-005 PR checklist item |

---

## 7. Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-001 | Clinical misuse of dose calculator if disclaimer is insufficiently prominent | Low | Critical | WARNING admonition; mandatory review by `/doctor-t1d-review` |
| R-002 | Screenshots become stale after UI changes | Medium | Medium | Developer re-runs script locally and commits updated images; PR checklist item (NFR-005) enforces this |
| R-003 | Playwright auth.setup.ts session expires during screenshot capture | Low | Low | Script re-authenticates at start; runs as a single Playwright project |
| R-004 | AsciiDoc `asciidoctor` build fails if image paths are wrong | Medium | Low | Integration test: `./gradlew asciidoctor` in CI |

---

## 8. Proposed Epic Structure

The implementation would be one epic with the following stories:

1. **Playwright screenshot script** — write `screenshots.spec.ts`; run against local podman stack as sarah; capture and commit all PNG files to `docs/images/patient-guide/`
2. **Part 1 — Getting Started** — login flow, navigation bar, patient banner
3. **Part 2 — Daily Workflow** — dashboard (including quick log buttons), measures, treatments, dose calculator, food database
4. **Part 3 — Analytics & Reports** — Analytics tab (HbA1c, TIR, AGP, basal/bolus averages, timeline); Report tab (all 11 sub-pages, date range, page selection, PDF download)
5. **Part 4 — Settings, Profiles & Integrations** — settings form, profiles (history, copy-on-write, proposed badge), Nightscout
6. **Glossary & cross-linking** — glossary (including DIA, CGP, PGR), link from `docs/index.adoc`
7. **Clinical review gate** — run `/doctor-t1d-review` against the complete guide; incorporate feedback; log ACCEPT verdict

---

## Approval

- [ ] triplem — Date: ___
