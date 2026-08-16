# Competitive / Reference Landscape — kdiab vs. the T1D Ecosystem

**Intent:** review technology and domain and suggest improvements
**Positioning (Q2):** kdiab is a **personal, self-hosted tool** — *not* competing for market share. This
analysis therefore reads the landscape as a **source of ideas to adopt**, not rivals to beat.
**Benchmark set (Q1):** Nightscout (classic), **Nocturne** (modern Nightscout successor), Tidepool.

> **Sources & confidence.** Nocturne facts are cited from its public repository
> (`github.com/nightscout/nocturne`, fetched 2026-08-15). Nightscout-classic and Tidepool descriptions
> are drawn from general ecosystem knowledge and are labelled **[verify]** where a claim should be
> confirmed before it drives a decision. Nothing here is presented as measured fact without a source.

## Side-by-side

| Dimension | **kdiab** | Nightscout (classic) [verify] | **Nocturne** (cited) | Tidepool [verify] |
|---|---|---|---|---|
| Purpose | Personal T1D mgmt platform (measures, treatments, profiles, carbs, calc, analytics) | Self-hosted CGM remote-monitoring | "Modern, high-performance diabetes management platform"; full Nightscout API rewrite | Open data platform + clinic tools |
| Backend | Kotlin + Ktor | Node.js/Express | **.NET 10 / C# / ASP.NET Core** | Node.js (services) |
| Frontend | React 19 + TS + Vite | JS (server-rendered + client) | **SvelteKit / Svelte** | React |
| Database | PostgreSQL + Exposed + Liquibase | MongoDB | **PostgreSQL + EF Core** | MongoDB / AWS |
| Architecture | **9 microservices**, hexagonal | Monolith | **Hybrid monolith API + modular connectors**, multi-tenant, cloud-native (.NET Aspire) | Cloud platform |
| Auth | Keycloak (JWT/JWKS), roles | API-secret / token | **JWT + WebAuthn/passkeys + OIDC** | Accounts/OAuth |
| Data ingestion | Nightscout-compat push (AAPS, xDrip+, Juggluco) | Uploader apps → API | **"Data Connectors": Dexcom, Glooko, LibreLinkUp, Medtronic, MyFitnessPal, Nightscout** | Device/partner uploads |
| Dose calculator | **Yes — `kdiab-calc`** (bolus from profile + CGM trend) | No | **No** (not mentioned) | No |
| Observability | OpenTelemetry (gRPC) | Minimal | **OpenTelemetry** | Platform-managed |
| Deployment | Docker/Podman compose (self-host) | Docker / Heroku-style | **Docker, K8s/Helm, .NET Aspire** | Managed SaaS |
| Tenancy | Single-tenant (userId from Keycloak) | Single-instance-per-user | **Multi-tenant** | Multi-tenant SaaS |
| License | (project) | AGPL-3.0 [verify] | **AGPL-3.0 + commercial** | BSD-2 [verify] |

## What the landscape suggests for kdiab (improvement signals)

Framed for a **personal self-hosted tool** — adopt selectively, ignore the "scale/multi-tenant" pressure.

1. **kdiab-calc is a genuine differentiator.** Neither Nightscout nor Nocturne ships a dose calculator;
   kdiab does. This is the asset most worth getting *clinically right* (aligns with your Q2 priority =
   clinical correctness). → feeds the build-vs-buy analysis.
2. **Convergent tech choices validate kdiab's stack.** Nocturne independently landed on
   PostgreSQL, OpenTelemetry, Docker, and Nightscout-API compatibility — the same spine as kdiab. This is
   reassurance, not a gap. *[interpretation]*
3. **Ingestion model gap [hypothesis].** Nocturne's explicit **"Data Connectors"** (Dexcom, LibreLinkUp,
   Glooko, Medtronic) are a *pull* integration pattern; kdiab currently relies on *push* via its
   Nightscout-compat layer (AAPS/xDrip+/Juggluco). A connector-style pull path could be a future
   improvement idea — **but** interoperability was deprioritized (Q6), so this is a parking-lot note, not
   a recommendation.
4. **Auth modernization signal.** Nocturne offers **passkeys (WebAuthn) + OIDC**. kdiab uses Keycloak,
   which *can* provide both — a concrete, low-risk hardening idea for the security priority (Q2=D):
   verify passkey/OIDC support is actually enabled in the kdiab Keycloak realm. *[verify against realm
   config]*
5. **Architecture contrast worth a decision, not a change.** kdiab runs **9 microservices** for a
   single-user tool; Nocturne runs a **modular monolith**. For a solo self-hosted deployment, the monolith
   footprint is lighter to operate. This tension (9 services vs. operational overhead for one maintainer)
   is a first-class question for **Application Design / architecture review** later — flag, don't decide
   here. *[interpretation]*

## Explicitly out of scope for this review

- Chasing Nocturne's **multi-tenant / K8s / cloud-native** posture — contradicts "personal self-hosted"
  (Q2=A) and "solo-maintainer-friendly" pragmatism. Noted and set aside.
- Broad interoperability parity (Dexcom/Glooko/etc. connectors) — Q6 deprioritized interoperability.
