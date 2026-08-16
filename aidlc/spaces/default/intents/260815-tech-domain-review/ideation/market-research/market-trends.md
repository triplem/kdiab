# Market / Ecosystem Trends — T1D Self-Hosted Tooling

**Intent:** review technology and domain and suggest improvements
**Relevance:** LOW-to-MODERATE. kdiab is a personal self-hosted tool (Q2=A), so "market" trends matter
only where they hint at **technical or domain improvements**. Kept brief by design.

> **Confidence labelling.** Claims are tagged `[cited]` (from a fetched source), `[well-established]`
> (widely known in the T1D OSS community), or `[hypothesis]` (my inference — verify before acting).

## Trends that intersect kdiab's priorities

1. **Modern rewrites of legacy T1D platforms.** `[cited]` Nocturne is an explicit ".NET 10 rewrite of the
   Nightscout API" (`github.com/nightscout/nocturne`, 2026-08-15). The ecosystem is moving from the
   original MongoDB/Node Nightscout toward **typed, relational, observability-first** stacks — which is
   exactly where kdiab already sits (Kotlin, PostgreSQL, OpenTelemetry). *Signal: kdiab's stack is current,
   not lagging.*

2. **Passkeys / WebAuthn + OIDC as the new auth baseline.** `[cited]` Nocturne ships WebAuthn/passkeys and
   OIDC. `[well-established]` Passwordless auth is becoming the default for health apps handling PII.
   *Signal for the security priority (Q2=D): confirm kdiab's Keycloak realm exposes passkeys/OIDC.*

3. **Closed-loop / automated insulin delivery (AID) is the domain's center of gravity.**
   `[well-established]` AndroidAPS, Loop, and OpenAPS define the reference dosing algorithms the community
   trusts. kdiab is **not** a closed-loop system, but its `kdiab-calc` bolus logic lives adjacent to this
   space — so alignment with published algorithm conventions matters for *trust*. *Feeds build-vs-buy.*

4. **Time-in-range (TIR) / AGP as the lingua franca of glucose analytics.** `[cited]` Nocturne highlights
   "time-in-range calculations"; `[well-established]` TIR + AGP (Ambulatory Glucose Profile) are the
   consensus clinical metrics. kdiab already implements AGP/HbA1c/timeline (`kdiab-analyze`). *Signal:
   verify kdiab's TIR/AGP computations match the consensus definitions (2019 international TIR targets).*

5. **Data-connector (pull) ingestion vs. uploader (push).** `[cited]` Nocturne integrates Dexcom,
   LibreLinkUp, Glooko, Medtronic as first-party connectors. `[hypothesis]` The ecosystem trend favors
   vendor-cloud pull over device-app push. kdiab uses push (Nightscout-compat). *Out of priority (Q6)
   but recorded as a landscape observation.*

## Net read

kdiab is **technologically well-positioned** for a personal tool — its stack mirrors where the modern
ecosystem is heading. The trends do **not** call for chasing scale or breadth. They sharpen exactly two
things already in your priority set: **auth hardening** (passkeys/OIDC) and **clinical-metric / dosing
correctness** (TIR/AGP definitions, dosing-algorithm alignment). Everything else is parking-lot.
