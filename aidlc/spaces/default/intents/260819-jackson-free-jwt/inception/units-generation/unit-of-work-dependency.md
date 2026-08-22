# Unit-of-Work Dependency — #1606 (Jackson-free JWT)

Traces to the application design (`../application-design/components.md`,
`../application-design/component-methods.md`, `../application-design/services.md`,
`../application-design/component-dependency.md`, `../application-design/decisions.md`),
`../requirements-analysis/requirements.md`, and `../user-stories/stories.md`.

## Unit DAG

There is **one** Unit of Work (U1), so the unit-level DAG is a single node with no inter-unit edges.

Machine-readable edge block (parsed by the runtime compiler into the Construction batch fan-out):

```yaml
units:
  - name: jackson-free-jwt
    depends_on: []
```

```
[ U1 — Jackson-free JWT verification ]   (no dependencies; one Bolt; one PR)
```
<!-- Text fallback: a single Unit of Work U1 (id: jackson-free-jwt) with no dependencies on other units. -->

The interesting dependency structure lives **inside** U1, as its task DAG (build order), reproduced
from `unit-of-work.md`:

```
T1 (parity tests) ─────────────┐
                                v
T2 (dep swap) ───────────────> T3 (Nimbus verifier) ──> T6 (sweep + jackson-pin removal) ──> T8 (release gate)
        │                        │                          ^
        └──> T4 (test minters) ──┘──────────────────────────┘
                                 └──> T5 (realm/config) ──> T7 (ADR)
```
<!-- Text fallback: T1 and T2 start; T3 depends on T1+T2; T4 depends on T2; T5 and T7 depend on T3 (T7 also on T5); T6 depends on T3+T4; T8 (release gate) depends on all. Critical path: T2 -> T3 -> T6 -> T8. -->

## No inter-unit dependencies

Because the change cannot be split without leaving `main` uncompilable (US-2 needs US-3/US-4 to
compile — see `unit-of-work.md`), there is no valid multi-unit decomposition. Construction runs U1 as a
single Bolt; stages 3.6 (Build & Test) and 3.7 (CI Pipeline) run once after it.

## Shared resources / cross-cutting

- **`kdiab-common/plugins/Security.kt`** is the single shared production file all tasks converge on
  (T3). No two tasks edit it concurrently — T1 writes tests, T3 writes the impl.
- **Per-service test sources** (T4) are independent per module (measures, profiles, treatments,
  analyze, carbs, calc, nightscout, users) — parallelisable within T4 but all inside U1.
- **Build files** (`libs.versions.toml` T2, `kdiab.kotlin-base` T6) — sequential (T6 after the sweep).
