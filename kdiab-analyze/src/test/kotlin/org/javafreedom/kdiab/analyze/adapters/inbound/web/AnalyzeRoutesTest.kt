@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.analyze.adapters.inbound.web

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.application.service.AnalyticsOperation
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageOperation
import org.javafreedom.kdiab.analyze.application.service.ProfilesOperation
import org.javafreedom.kdiab.analyze.application.service.TimelineOperation
import org.javafreedom.kdiab.analyze.domain.model.AgpBucketData
import org.javafreedom.kdiab.analyze.domain.model.AgpResult
import org.javafreedom.kdiab.analyze.domain.model.DeviceUsageResult
import org.javafreedom.kdiab.analyze.domain.model.Hba1cResult
import org.javafreedom.kdiab.analyze.domain.model.ProfilesResult
import org.javafreedom.kdiab.analyze.domain.model.ReportSummaryResult
import org.javafreedom.kdiab.analyze.domain.model.TirBreakdown
import org.javafreedom.kdiab.analyze.domain.model.TirResult
import org.javafreedom.kdiab.analyze.domain.model.TirZone
import org.javafreedom.kdiab.analyze.domain.model.Timeline
import org.javafreedom.kdiab.analyze.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication lambdas.
private fun Application.installMockDi(
    timelineService: TimelineOperation,
    analyticsService: AnalyticsOperation,
    profilesService: ProfilesOperation,
    deviceUsageService: DeviceUsageOperation,
) {
    install(DI) { }
    dependencies {
        provide<TimelineOperation> { timelineService }
        provide<AnalyticsOperation> { analyticsService }
        provide<ProfilesOperation> { profilesService }
        provide<DeviceUsageOperation> { deviceUsageService }
        provide<TreatmentsClient> { mockk(relaxed = true) }
    }
}

class AnalyzeRoutesTest {

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private companion object {
        const val JWT_SECRET = "test-secret-for-analyze-tests-hs256"
        const val AUDIENCE   = "analyze"
        const val ISSUER     = "http://localhost:8085/realms/kdiab-analyze"

        const val SARAH_ID  = "11111111-1111-1111-1111-111111111111"
        const val MIKE_ID   = "22222222-2222-2222-2222-222222222222"
        const val DOCTOR_ID = "33333333-3333-3333-3333-333333333333"
        const val ADMIN_ID  = "55555555-5555-5555-5555-555555555555"

        const val FROM = "2024-01-01T00:00:00Z"
        const val TO   = "2024-01-31T23:59:59Z"

        fun token(
            userId: String,
            roles: List<String>,
            allowedPatients: List<String> = emptyList(),
        ): String = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder()
            .subject(userId)
            .audience(listOf(AUDIENCE, "measure", "profile", "treatment"))
            .issuer(ISSUER)
            .claim("roles", roles)
            .apply { if (allowedPatients.isNotEmpty()) claim("allowed_patients", allowedPatients) }
            .build()).apply { sign(MACSigner(JWT_SECRET.toByteArray())) }.serialize()

        val sarahToken  get() = token(SARAH_ID,  listOf("PATIENT"))
        val mikeToken   get() = token(MIKE_ID,   listOf("PATIENT"))
        val doctorToken get() = token(DOCTOR_ID, listOf("DOCTOR"), listOf(SARAH_ID))
        val adminToken  get() = token(ADMIN_ID,  listOf("ADMIN"))
    }

    // ── Stubs ─────────────────────────────────────────────────────────────────

    private val emptyTimeline = Timeline(emptyList(), emptyList())
    private val emptyHba1c = Hba1cResult(hba1c = null, meanGlucose = 0.0, readingCount = 0, tir = TirBreakdown())
    private val emptyAgp = AgpResult((0 until 288).map { i ->
        AgpBucketData(minuteOfDay = i * 5, p10 = 0.0, p25 = 0.0, median = 0.0, p75 = 0.0, p90 = 0.0, count = 0)
    })
    private val emptyProfiles = ProfilesResult(emptyList())
    private val emptyDeviceUsage = DeviceUsageResult(
        userId = SARAH_ID,
        avgSensorDays = null, stddevSensorDays = null,
        avgCatheterDays = null, stddevCatheterDays = null,
        avgReservoirDays = null, stddevReservoirDays = null,
        avgBatteryDays = null, stddevBatteryDays = null,
    )

    // ── Test application setup ────────────────────────────────────────────────

    private fun routeTest(
        block: suspend ApplicationTestBuilder.(
            timelineService: TimelineOperation,
            analyticsService: AnalyticsOperation,
            profilesService: ProfilesOperation,
            deviceUsageService: DeviceUsageOperation,
        ) -> Unit,
    ) {
        val timelineService     = mockk<TimelineOperation>()
        val analyticsService    = mockk<AnalyticsOperation>()
        val profilesService     = mockk<ProfilesOperation>()
        val deviceUsageService  = mockk<DeviceUsageOperation>()
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"   to ISSUER,
                    "jwt.audience" to AUDIENCE,
                    "jwt.realm"    to "kdiab-analyze",
                    "jwt.test"     to "true",
                    "jwt.secret"   to JWT_SECRET,
                )
            }
            application {
                installMockDi(timelineService, analyticsService, profilesService, deviceUsageService)
                module()
            }
            block(timelineService, analyticsService, profilesService, deviceUsageService)
        }
    }

    // ── Helper: build URL with timeframe params ───────────────────────────────

    private fun timelineUrl(userId: String)      = "/api/v1/users/$userId/timeline?from=$FROM&to=$TO"
    private fun hba1cUrl(userId: String)         = "/api/v1/users/$userId/analytics/hba1c?from=$FROM&to=$TO"
    private fun agpUrl(userId: String)           = "/api/v1/users/$userId/analytics/agp?from=$FROM&to=$TO"
    private fun profilesUrl(userId: String)      = "/api/v1/users/$userId/profiles/active?from=$FROM&to=$TO"
    private fun deviceUsageUrl(userId: String)   = "/api/v1/users/$userId/analytics/device-usage"

    // ── 401 — no auth token ───────────────────────────────────────────────────

    @Test
    fun `timeline - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get(timelineUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `hba1c - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get(hba1cUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `agp - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get(agpUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `profiles active - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get(profilesUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ── PATIENT — reads own data (200) ────────────────────────────────────────

    @Test
    fun `timeline - 200 patient reads own data`() = routeTest { svc, _, _, _ ->
        coEvery { svc.getTimeline(SARAH_ID, FROM, TO, any(), any()) } returns emptyTimeline
        val resp = client.get(timelineUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `hba1c - 200 patient reads own data`() = routeTest { _, svc, _, _ ->
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } returns Unit
        coEvery { svc.getHba1c(SARAH_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyHba1c
        val resp = client.get(hba1cUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `agp - 200 patient reads own data`() = routeTest { _, svc, _, _ ->
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } returns Unit
        coEvery { svc.getAgp(SARAH_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyAgp
        val resp = client.get(agpUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `profiles active - 200 patient reads own data`() = routeTest { _, _, svc, _ ->
        coEvery { svc.getProfiles(SARAH_ID, any(), any()) } returns emptyProfiles
        val resp = client.get(profilesUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── PATIENT — reads another user's data (403) ─────────────────────────────

    @Test
    fun `timeline - 403 patient reads another user data`() = routeTest { _, _, _, _ ->
        val resp = client.get(timelineUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `hba1c - 403 patient reads another user data`() = routeTest { _, _, _, _ ->
        val resp = client.get(hba1cUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `agp - 403 patient reads another user data`() = routeTest { _, _, _, _ ->
        val resp = client.get(agpUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `profiles active - 403 patient reads another user data`() = routeTest { _, _, _, _ ->
        val resp = client.get(profilesUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── DOCTOR — reads allowed patient data (200) ─────────────────────────────

    @Test
    fun `timeline - 200 doctor reads allowed patient data`() = routeTest { svc, _, _, _ ->
        coEvery { svc.getTimeline(SARAH_ID, FROM, TO, any(), any()) } returns emptyTimeline
        val resp = client.get(timelineUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `hba1c - 200 doctor reads allowed patient data`() = routeTest { _, svc, _, _ ->
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } returns Unit
        coEvery { svc.getHba1c(SARAH_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyHba1c
        val resp = client.get(hba1cUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `agp - 200 doctor reads allowed patient data`() = routeTest { _, svc, _, _ ->
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } returns Unit
        coEvery { svc.getAgp(SARAH_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyAgp
        val resp = client.get(agpUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `profiles active - 200 doctor reads allowed patient data`() = routeTest { _, _, svc, _ ->
        coEvery { svc.getProfiles(SARAH_ID, any(), any()) } returns emptyProfiles
        val resp = client.get(profilesUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── DOCTOR — reads non-allowed patient data (403) ─────────────────────────

    @Test
    fun `timeline - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _, _ ->
        val resp = client.get(timelineUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `hba1c - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _, _ ->
        val resp = client.get(hba1cUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `agp - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _, _ ->
        val resp = client.get(agpUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `profiles active - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _, _ ->
        val resp = client.get(profilesUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── ADMIN — reads any user's data (200) ──────────────────────────────────

    @Test
    fun `timeline - 200 admin reads any user data`() = routeTest { svc, _, _, _ ->
        coEvery { svc.getTimeline(MIKE_ID, FROM, TO, any(), any()) } returns emptyTimeline
        val resp = client.get(timelineUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `hba1c - 200 admin reads any user data`() = routeTest { _, svc, _, _ ->
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } returns Unit
        coEvery { svc.getHba1c(MIKE_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyHba1c
        val resp = client.get(hba1cUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `agp - 200 admin reads any user data`() = routeTest { _, svc, _, _ ->
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } returns Unit
        coEvery { svc.getAgp(MIKE_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyAgp
        val resp = client.get(agpUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `profiles active - 200 admin reads any user data`() = routeTest { _, _, svc, _ ->
        coEvery { svc.getProfiles(MIKE_ID, any(), any()) } returns emptyProfiles
        val resp = client.get(profilesUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── Graceful degradation: pre-fetch throws ────────────────────────────────

    @Test
    fun `hba1c - 200 when preFetchCgmMeasures throws`() = routeTest { _, svc, _, _ ->
        // preFetchCgmMeasures failure is swallowed by the service's runCatching —
        // the route should still complete successfully using the threshold values.
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } throws
            RuntimeException("upstream measures service unavailable")
        coEvery { svc.getHba1c(SARAH_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyHba1c
        val resp = client.get(hba1cUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `agp - 200 when preFetchCgmMeasures throws`() = routeTest { _, svc, _, _ ->
        // preFetchCgmMeasures failure is swallowed by the service's runCatching —
        // the route should still complete successfully using the threshold values.
        coEvery { svc.getAnalysisThresholds(any(), any(), any()) } returns Pair(70.0, 180.0)
        coEvery { svc.preFetchCgmMeasures(any(), any(), any(), any(), any()) } throws
            RuntimeException("upstream measures service unavailable")
        coEvery { svc.getAgp(SARAH_ID, FROM, TO, any(), any(), any(), any(), any()) } returns emptyAgp
        val resp = client.get(agpUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── Missing query params (400) ────────────────────────────────────────────

    @Test
    fun `timeline - 404 when from param is missing`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/timeline?to=$TO") { bearerAuth(sarahToken) }
        // Ktor Resources returns 404 when required query params are missing (route fails to bind)
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `hba1c - 404 when to param is missing`() = routeTest { _, _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/analytics/hba1c?from=$FROM") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    // ── Missing upstream audiences (403) ─────────────────────────────────────

    @Test
    fun `timeline - 403 when JWT lacks upstream audiences`() = routeTest { _, _, _, _ ->
        val tokenWithoutUpstreamAudiences = SignedJWT(JWSHeader(JWSAlgorithm.HS256), JWTClaimsSet.Builder()
            .subject(SARAH_ID)
            .audience(AUDIENCE)
            .issuer(ISSUER)
            .claim("roles", listOf("PATIENT"))
            .build()).apply { sign(MACSigner(JWT_SECRET.toByteArray())) }.serialize()
        val resp = client.get(timelineUrl(SARAH_ID)) { bearerAuth(tokenWithoutUpstreamAudiences) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── Device usage analytics ────────────────────────────────────────────────

    @Test
    fun `device-usage - 401 without auth token`() = routeTest { _, _, _, _ ->
        val resp = client.get(deviceUsageUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `device-usage - 200 patient reads own data`() = routeTest { _, _, _, svc ->
        coEvery { svc.compute(SARAH_ID, any(), any(), any()) } returns emptyDeviceUsage
        val resp = client.get(deviceUsageUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `device-usage - 403 patient reads another user data`() = routeTest { _, _, _, _ ->
        val resp = client.get(deviceUsageUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `device-usage - 200 admin reads any user data`() = routeTest { _, _, _, svc ->
        coEvery { svc.compute(MIKE_ID, any(), any(), any()) } returns emptyDeviceUsage.copy(userId = MIKE_ID)
        val resp = client.get(deviceUsageUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── report-summary displayName parameter ──────────────────────────────────

    private val emptyTirZone = TirZone(count = 0, percent = 0.0)
    private val emptyTirResult = TirResult(
        veryLow = emptyTirZone,
        low = emptyTirZone,
        inRange = emptyTirZone,
        high = emptyTirZone,
        veryHigh = emptyTirZone,
    )
    private fun stubReportSummary(displayName: String) = ReportSummaryResult(
        displayName = displayName,
        daysAnalysed = 0, cgmReadingCount = 0, cgmIntervalMinutes = 5,
        insulinTypes = emptyList(), insulinChanges = 0,
        avgDaysPerCartridge = null, siteChanges = 0, avgDaysPerSite = null,
        sensorInserts = 0, avgDaysPerSensor = null,
        tirProfile = emptyTirResult, tirStandard = emptyTirResult,
        minGlucose = null, maxGlucose = null, meanGlucose = null,
        sd = null, gvi = null, pgs = null, gri = null, griZone = null, eHbA1c = null,
        avgCarbsPerDayG = null, avgBolusPerDayIe = null, bolusPercent = null,
        avgBasalPerDayIe = null, basalPercent = null, avgTotalInsulinPerDayIe = null,
    )

    private fun reportSummaryUrl(userId: String, displayName: String? = null): String {
        val base = "/api/v1/users/$userId/analytics/report-summary?from=$FROM&to=$TO"
        return if (displayName != null) "$base&displayName=$displayName" else base
    }

    @Test
    fun `report-summary - displayName query param is forwarded to service`() = routeTest { _, svc, _, _ ->
        val capturedDisplayName = slot<String>()
        coEvery {
            svc.getReportSummary(
                userId = SARAH_ID,
                displayName = capture(capturedDisplayName),
                from = any(),
                to = any(),
                authorization = any(),
                glucoseUnit = any(),
                correlationId = any(),
                timeZone = any(),
            )
        } answers { stubReportSummary(capturedDisplayName.captured) }

        val resp = client.get(reportSummaryUrl(SARAH_ID, "Jane+Doe")) { bearerAuth(sarahToken) }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals("Jane Doe", capturedDisplayName.captured)
    }

    @Test
    fun `report-summary - userId UUID used as displayName when param is absent`() = routeTest { _, svc, _, _ ->
        val capturedDisplayName = slot<String>()
        coEvery {
            svc.getReportSummary(
                userId = SARAH_ID,
                displayName = capture(capturedDisplayName),
                from = any(),
                to = any(),
                authorization = any(),
                glucoseUnit = any(),
                correlationId = any(),
                timeZone = any(),
            )
        } answers { stubReportSummary(capturedDisplayName.captured) }

        val resp = client.get(reportSummaryUrl(SARAH_ID)) { bearerAuth(sarahToken) }

        assertEquals(HttpStatusCode.OK, resp.status)
        assertEquals(SARAH_ID, capturedDisplayName.captured)
    }
}
