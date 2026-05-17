package org.javafreedom.kdiab.treatments.contract

/**
 * Placeholder for consumer-driven contract tests between kdiab-analyze (BFF) and
 * kdiab-treatments (upstream provider). Tracked in issue #535.
 *
 * When implemented, these tests should use Pact JVM to verify that:
 *
 * 1. **Consumer side** (kdiab-analyze / TreatmentsClient):
 *    - Pact interactions are recorded against a Pact mock server.
 *    - The generated pact JSON file is published to a Pact Broker or shared as a file.
 *
 * 2. **Provider side** (kdiab-treatments):
 *    - The provider verifier replays recorded pact interactions against the real Ktor
 *      application (started via `testApplication`).
 *    - Responses are compared against the expectations recorded by the consumer.
 *
 * Relevant Pact JVM dependency (add to build.gradle.kts when implementing):
 *   testImplementation("au.com.dius.pact.provider:junit5:4.x.x")
 *   testImplementation("au.com.dius.pact.consumer:junit5:4.x.x")
 *
 * Interactions to cover:
 *   - GET /api/v1/users/{userId}/treatments — list with date range filters
 *   - GET /api/v1/users/{userId}/treatments (with type filter) — used for device-age endpoint
 *   - POST /api/v1/users/{userId}/treatments — create bolus, carbs, site-change, sensor-insert
 *   - POST /api/v1/users/{userId}/treatments/archive — archive bulk
 *
 * Reference: https://docs.pact.io/implementation_guides/jvm
 * Issue: https://github.com/triplem/kdiab/issues/535
 */
@Suppress("UnnecessaryAbstractClass")
abstract class ContractTestsPlaceholder
