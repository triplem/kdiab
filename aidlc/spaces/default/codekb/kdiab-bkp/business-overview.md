# Business Overview — kdiab (T1D Management Platform)

## Business Domain

kdiab is a **Type 1 Diabetes (T1D) management platform**. Type 1 Diabetes is an
autoimmune condition in which the pancreas produces little or no insulin, so people
living with it must continuously balance blood glucose against food (carbohydrates),
insulin delivery (via pen or pump), and physical activity. Poor balance carries real
clinical risk: hypoglycaemia (dangerously low glucose) is an acute emergency, while
sustained hyperglycaemia (high glucose) drives long-term complications. The platform's
purpose is to help patients and their care teams **record, understand, and act on**
the data that governs this balance.

The platform is a data-and-analytics system, not a therapy-delivery device: it does
not drive an insulin pump directly. Instead it aggregates measurement and treatment
data, computes clinical analytics (HbA1c estimate, Ambulatory Glucose Profile, time-in-range,
glucose timeline), offers a bolus-dose calculator as a decision aid, and interoperates
with the established diabetes tooling ecosystem (AAPS, xDrip+, Juggluco) through a
Nightscout-compatible facade.

## Purpose and Value

- **For patients** — a single place to log and review CGM/BGM readings, insulin boluses
  and basal changes, carbohydrate intake, and body measurements; to see a unified analytics
  dashboard; and to get a suggested bolus dose from their own profile and current glucose trend.
- **For clinicians (doctors)** — read access to the data of the patients assigned to them,
  so consultations are grounded in real 24/7 data rather than recall.
- **For the wider ecosystem** — a Nightscout API compatibility layer so that patients can
  keep using their existing looping/monitoring apps while their data flows into kdiab.

## Key Functionality (by clinical / data domain)

The platform is decomposed into focused services, each owning one clinical or data domain:

| Domain | Service | What it owns |
|---|---|---|
| **Measurements** | kdiab-measures | CGM (continuous glucose), BGM (fingerstick), blood pressure, weight, pulse — the primary time-series measurement store. |
| **Basal profiles** | kdiab-profiles | Insulin-pump basal-rate profiles with copy-on-write versioning and an ACTIVE / ARCHIVED lifecycle. |
| **Treatments** | kdiab-treatments | Discrete treatment events: bolus, basal changes, carbs, corrections, plus device status. |
| **Carbohydrates** | kdiab-carbs | Food / carbohydrate database and per-meal carb entries. |
| **Users & relationships** | kdiab-users | User settings, doctor–patient relationships, invitations, and API keys; integrates with Keycloak for identity. |
| **Analytics (read model)** | kdiab-analyze | Stateless Backend-for-Frontend that fans out to measures/treatments/profiles and computes timeline, AGP, HbA1c, and device-usage analytics. |
| **Dose calculation** | kdiab-calc | Stateless bolus-recommendation calculator from an active profile plus current CGM trend. |
| **Ecosystem interop** | kdiab-nightscout | Nightscout API v1 + v3 compatibility facade for AAPS, xDrip+, and Juggluco. |
| **Shared foundation** | kdiab-common | Cross-cutting domain types and Ktor plugins shared by every service. |

The user-facing surface is a single React SPA (**kdiab-ui**) organised by feature
(analytics, calc, carbs, dashboard, measures, profiles, report, timeframe, timeline,
treatments, users).

## Actors and Access Model

Identity is provided by **Keycloak** and carried in the JWT; there is deliberately **no
Users table** — the user identity is the JWT `sub` claim. Three roles exist:

- **PATIENT** — owns and manages their own data. Each patient carries a preferred glucose
  unit (mg/dL or mmol/L) that flows through analytics and display.
- **DOCTOR** — read-only access to the data of an explicitly assigned set of patients
  (`allowedPatients`). A doctor cannot see a patient they are not assigned to.
- **ADMIN** — platform-wide administrative access.

Access control is **attribute-based (ABAC)** and centralised in a single rule:
`UserPrincipal.canAccess(targetUserId)` grants access when the caller is the subject
themselves, is an admin, or is a doctor whose `allowedPatients` set contains the target.
This single choke-point is the platform's core authorisation invariant.

## Business Context Notes

- **Safety-adjacent, not a medical device controller.** The bolus calculator is a decision
  aid; the platform surfaces information for clinical judgement rather than autonomously
  dosing insulin. This framing matters for how correctness and clinical review are treated.
- **Interoperability is a first-class requirement.** Real patients already run a stack of
  community tools; kdiab meets them where they are via the Nightscout compatibility facade
  rather than forcing migration.
- **Glucose-unit duality is pervasive.** mg/dL vs mmol/L is a per-patient concern that the
  measurement, analytics, and calculation domains must all respect consistently.
