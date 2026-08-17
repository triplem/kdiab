# Refined Mockups — Technology & Domain Review

**Status: NOT APPLICABLE** — no user-facing UI is designed or built in this intent.

> Consulted upstream: `wireframes.md`, `user-flow.md` (both N/A), `stories.md`, `requirements.md`,
> `team-practices.md`.

## Why there is nothing to mock up

This initiative produces **recommendations** — a prioritized, evidence-linked GitHub-issue backlog,
a quick-wins list, and a phased roadmap (`requirements.md` FR-D.1–D.3; `stories.md` US-7/8/9). No new
screens, components, or flows are being designed. The ideation `wireframes.md` and `user-flow.md` are
both marked NOT APPLICABLE for the same reason; refined-mockups carries that decision forward.

Per `stories.md`, every story (US-1…US-10) is an assessment/deliverable story, not a UI feature — so
there is no user-story-to-screen mapping to refine.

## The only "layout" decision — presentation of the review output

The sole presentation choice (already settled in ideation and requirements, not a visual design task):

- **GitHub issues** — one per finding, labelled by theme + severity, evidence-linked (FR-D.1).
- **Roadmap document** — phased near/mid/long, value-density ordered (FR-D.3).
- **Quick-wins list** — a short actionable subset (FR-D.2).

These are Markdown/GitHub artifacts committed under `docs/review/` (per FR-D.1); they need no visual
mockups, component library, or responsive design. Consistent with `team-practices.md`, each is
authored on a feature branch and merged via the standard green-CI gate.

## If UI work emerges later

Should the review surface a *frontend* recommendation (e.g. a `kdiab-ui` item) that grows into a
feature, that feature starts a **new intent** and runs its own Rough/Refined Mockups then.
