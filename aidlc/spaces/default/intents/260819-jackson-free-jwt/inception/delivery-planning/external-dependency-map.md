# External Dependency Map — Jackson-free JWT Verification (#1606)

Traces to `../application-design/components.md`, `../units-generation/unit-of-work.md`,
`../units-generation/unit-of-work-dependency.md`, `../units-generation/unit-of-work-story-map.md`,
`../requirements-analysis/requirements.md`, `../user-stories/stories.md`,
`../refined-mockups/mockups.md` (skipped — no UI); governed by
`../practices-discovery/team-practices.md`.

## External Dependencies

| Dependency | Kind | Change | Risk / note |
|---|---|---|---|
| `com.nimbusds:nimbus-jose-jwt` (≈10.0.1) | New runtime lib (Maven Central) | **Added** to the version catalog | Jackson-free (uses `json-smart`); mature/audited; verify Trivy/CodeQL clean on the new transitives (`json-smart`, `accessors-smart`, `asm`) |
| `io.ktor:ktor-server-auth-jwt` | Existing lib | **Removed** from the ktor-server bundle | Sole source of java-jwt + jwks-rsa + jackson |
| `io.ktor:ktor-server-auth` (base) | Existing lib | **Kept** | Hosts the custom `AuthenticationProvider`; jackson-free |
| **Keycloak** (identity provider) | External service | **Unchanged** | JWKS endpoint + RS256 token/claim contract stay as-is; verification-only change |
| `net.minidev:json-smart` (+ accessors-smart, asm) | New transitive (via Nimbus) | Added | Jackson-free; monitor its own CVE surface (Trivy/CodeQL in CI) |
| Maven Central / GitHub Packages | Artifact repos | Unchanged | `nimbus-jose-jwt` resolves from Maven Central (already configured) |

## Supply-chain / gate dependencies

- **CI gates** (Trivy CRITICAL/HIGH, CodeQL, SonarCloud, SBOM/CycloneDX) must stay green with the new
  Nimbus transitives — a merge gate (US-8).
- **No new infrastructure**, no new network egress beyond the existing Keycloak JWKS fetch, no new
  datastore, no new container.

## Blocking / external coordination

**None.** The change is self-contained; no external team, no API contract change, no coordinated
deploy. Epic #1603 (parent) closes on merge — the only "dependency" is that #1606 is its last open child.
