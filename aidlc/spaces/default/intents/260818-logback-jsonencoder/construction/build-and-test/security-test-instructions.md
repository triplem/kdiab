# Security Test Instructions — logback-jsonencoder

This change is **security-positive** — its purpose is to shrink the attack surface by removing the
jackson stack (a recurring CVE source, force-pinned for CVE-2026-54512/54513).

## Checks (supports FR-7 / AC-6)

### 1. jackson gone from every backend (AC-1, the core security win)

```bash
for s in analyze calc carbs measures nightscout profiles treatments users; do
  (cd kdiab-$s && ./gradlew -q dependencies --configuration runtimeClasspath) \
    | grep -iE 'jackson|logback-contrib' && echo "BAD $s" || echo "clean $s"
done   # expect all "clean"
```

### 2. SBOM no longer lists jackson / logback-contrib

```bash
./gradlew cyclonedxBom     # per service; inspect the generated bom.json
grep -iE 'jackson|logback-contrib' <bom.json>   # expect empty
```

### 3. Trivy has no jackson finding to pin (CI)

- The `jackson-core` / `jackson-databind` force-pins were removed from
  `kdiab.kotlin-base.gradle.kts`; with jackson off the classpath there is nothing for Trivy to flag.
- The **handlebars** constraint (CVE-2026-55760) is retained — verify it is still present.

### 4. No secrets / PII in logs (NFR-4)

The set of MDC keys and message content is unchanged; only the serializer changed. No new field
exposes sensitive data. `Correlation-ID` (non-sensitive) remains under `mdc`.

CI (CodeQL, Trivy CRITICAL/HIGH, SBOM) is the authoritative security gate before merge.
