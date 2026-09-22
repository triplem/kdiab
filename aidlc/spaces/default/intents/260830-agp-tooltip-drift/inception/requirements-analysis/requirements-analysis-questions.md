# Requirements Analysis — Clarifying Questions

Intent: **fix the agp chart tooltip drift** (bugfix scope)

Component under analysis: `kdiab-ui/src/features/analytics/AgpChart.tsx`
(reused by the print/PDF page `kdiab-ui/src/features/report/AgpChartPage.tsx`).

Working diagnosis: the `<XAxis dataKey="minuteOfDay">` has no `type` prop, so Recharts
defaults to a **category** axis. Points are placed by index (not by numeric minute), null-filtered
gaps compress the remaining points, and the tooltip snaps to the category index — so its time label
and percentile values drift away from the x-position under the cursor.

Answer each by writing your choice after `[Answer]:` (A–E, or X with your own text).

---

## Q1 — What exactly does the "drift" look like to you?

A. The tooltip's **time label** (e.g. "09:00") doesn't match the time under the cursor
B. The tooltip shows the **wrong percentile values** for the point I'm hovering
C. The **median line / bands** appear shifted relative to the x-axis hour ticks
D. All of the above — the whole x mapping is off
E. It only misbehaves in the **printed / PDF report**, not the live chart
X. Other (please specify)

[Answer]: D — All of the above; the whole x mapping is off (confirms the category-axis diagnosis)

---

## Q2 — Should the fix cover the print/PDF report page as well as the live chart?

Both surfaces render the same `AgpChart` component, so a single fix normally covers both.

A. Yes — both the live analytics chart and the print/PDF report must be correct
B. Live chart only; the print page is out of scope
C. Print/PDF only
X. Other (please specify)

[Answer]: A — Both the live analytics chart and the print/PDF report must be correct

---

## Q3 — Acceptance / regression guard: what proves it's fixed?

A. Add a unit/component test asserting the tooltip label matches the hovered bucket's time, plus manual verification in the browser
B. A unit/component test is enough; no manual step required
C. Manual browser verification is enough; no new automated test
D. Whatever satisfies the project's 80% coverage gate on changed lines
X. Other (please specify)

[Answer]: A — Add a unit/component test asserting the tooltip label matches the hovered bucket's time, plus manual browser verification

---

## Q4 — Visual constraint: may the x-axis rendering change slightly?

Switching to a linear numeric time axis is the natural fix. It should look nearly identical, but
tick placement (0,3,6,…,24h) becomes exactly proportional and the ⓘ/legend/band styling stays as-is.

A. Yes — a correct linear time axis is the goal; minor tick-spacing changes are fine as long as it stays readable and accessible
B. No — the visual output must be pixel-identical except for the drift fix
C. Prefer minimal change, but correctness wins over pixel-identity
X. Other (please specify)

[Answer]: C — Prefer minimal change, but correctness wins over pixel-identity. (Asked in a follow-up guided round after the §12a reviewer flagged it as un-asked; user confirmed "Correctness wins".)

---

## Q5 — Should I open a GitHub issue for this before code-generation?

Project rule: every change references an issue (`Closes #N`) and follows Conventional Commits.

A. Yes — create a GitHub issue now and reference it in the branch + commits
B. No — I'll create/track it myself
C. An issue already exists — I'll give you the number
X. Other (please specify)

[Answer]: A — Create a GitHub issue now and reference it in the branch + commits
