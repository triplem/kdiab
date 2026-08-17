# Personas — Technology & Domain Review

Personas for the review intent. The **maintainer** consumes the review deliverables directly; the
**patient** and **doctor** are indirect beneficiaries whose safety the clinical-correctness findings
protect (per `business-overview.md` and `requirements.md`).

## Sam — Solo Maintainer (Primary)

- **Role:** Sole owner, architect, implementer, and audience for the kdiab platform.
- **Goals:** Know, with evidence, where the platform is strong vs. weak across technology health and
  T1D domain correctness; get a prioritized, incrementally-executable plan for "where to invest next".
- **Pain points:** No systematic health-check exists; improvement effort risks being ad hoc; limited,
  occasional-burst capacity means every recommendation must be independently shippable.
- **Context:** Works trunk-based with feature-branch-per-issue and a green-CI gate (`team-practices.md`);
  values pragmatism and self-hostability; is the sole decision-maker (no committee sign-off).
- **Priority:** 1 (primary — the deliverables serve Sam's investment decision).

## Priya — T1D Patient on Insulin Pump + CGM (Indirect)

- **Role:** End user of the platform's data; relies on correct dose recommendations and metrics.
- **Goals:** Trust that a bolus recommendation and the displayed CGM/TIR/AGP metrics are clinically
  correct and safe.
- **Pain points:** A wrong dose calc or mis-defined metric is a direct safety risk she cannot audit.
- **Context:** Does not see the review; benefits when clinical-safety findings are prioritized and fixed.
- **Priority:** 2 (indirect — the "so that" behind the non-negotiable clinical-safety theme).

## Dr. Chen — Endocrinologist (Indirect)

- **Role:** Doctor reviewing patient data (TIR, AGP, HbA1c/GMI, treatment history) to guide therapy.
- **Goals:** Make therapy decisions on metrics that match standard clinical definitions.
- **Pain points:** A mis-defined AGP or HbA1c/GMI could subtly mislead a therapy decision.
- **Context:** Accesses patient data via the doctor-patient relationship (ABAC `canAccess`); benefits
  when metric-definition correctness is verified.
- **Priority:** 2 (indirect — reinforces the clinical-correctness theme's real-world stake).

## Relationships & Priority Ranking

Sam (primary consumer) → acts on the review → fixes prioritized findings → protects Priya and Dr. Chen
(indirect beneficiaries). The review's value-density ordering (clinical safety first) exists precisely
because Priya's and Dr. Chen's safety outrank convenience or modernization.
