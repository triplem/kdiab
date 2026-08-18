# Build Instructions — logback-jsonencoder

Composite Gradle build (`includeBuild` per service). Change is config + build-metadata only (no `.kt`).

## Build the backends

```bash
# Compile + assemble all 8 services + kdiab-common (no infra needed)
./gradlew buildBackends
# Compile-only (fastest sanity, no tests)
./gradlew compileBackends
```

## The authoritative gate (matches team practice: green before merge)

```bash
./gradlew check          # unit tests + Detekt + Kover ≥ 80%, all backends
```

`check` aggregates each included build's `:check` (see root `build.gradle.kts`). Integration/e2e
(`integrationTest`, `e2eTest`) are separate `shouldRunAfter` tasks and are NOT pulled into `check`,
so no Postgres/Keycloak is required for this stage.

## The #1556-specific acceptance check (AC-1)

```bash
for s in analyze calc carbs measures nightscout profiles treatments users; do
  (cd kdiab-$s && ./gradlew -q dependencies --configuration runtimeClasspath) \
    | grep -iE 'jackson|logback-contrib' && echo "  BAD: $s" || echo "  clean: $s"
done
```

Expected: **empty** (no jackson / logback-contrib) for all 8.

## Notes

- On ≤ 8 GB RAM add `--no-parallel` (composite builds start one compiler daemon per included build).
- Detekt runs per module (each included build is its own Gradle root).
