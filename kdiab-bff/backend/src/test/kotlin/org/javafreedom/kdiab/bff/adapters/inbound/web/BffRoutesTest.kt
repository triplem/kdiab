@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.bff.adapters.inbound.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.bff.application.service.AnalyticsService
import org.javafreedom.kdiab.bff.application.service.ProfilesService
import org.javafreedom.kdiab.bff.application.service.TimelineService
import org.javafreedom.kdiab.bff.domain.model.AgpHourlyData
import org.javafreedom.kdiab.bff.domain.model.AgpResult
import org.javafreedom.kdiab.bff.domain.model.Hba1cResult
import org.javafreedom.kdiab.bff.domain.model.ProfilesResult
import org.javafreedom.kdiab.bff.domain.model.TirBreakdown
import org.javafreedom.kdiab.bff.domain.model.Timeline
import org.javafreedom.kdiab.bff.module

class BffRoutesTest {

    // ── JWT helpers ───────────────────────────────────────────────────────────

    private companion object {
        const val JWT_SECRET = "test-secret-for-bff-tests"
        const val AUDIENCE   = "bff"
        const val ISSUER     = "http://localhost:8081/realms/kdiab-bff"

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
        ): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .apply { if (allowedPatients.isNotEmpty()) withClaim("allowed_patients", allowedPatients) }
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val sarahToken  get() = token(SARAH_ID,  listOf("PATIENT"))
        val mikeToken   get() = token(MIKE_ID,   listOf("PATIENT"))
        val doctorToken get() = token(DOCTOR_ID, listOf("DOCTOR"), listOf(SARAH_ID))
        val adminToken  get() = token(ADMIN_ID,  listOf("ADMIN"))
    }

    // ── Stubs ─────────────────────────────────────────────────────────────────

    private val emptyTimeline = Timeline(emptyList(), emptyList())
    private val emptyHba1c = Hba1cResult(hba1c = null, meanGlucose = 0.0, readingCount = 0, tir = TirBreakdown())
    private val emptyAgp = AgpResult((0 until 24).map { h ->
        AgpHourlyData(hour = h, p10 = 0.0, p25 = 0.0, median = 0.0, p75 = 0.0, p90 = 0.0, count = 0)
    })
    private val emptyProfiles = ProfilesResult(emptyList())

    // ── Test application setup ────────────────────────────────────────────────

    private fun routeTest(
        block: suspend ApplicationTestBuilder.(
            timelineService: TimelineService,
            analyticsService: AnalyticsService,
            profilesService: ProfilesService,
        ) -> Unit,
    ) {
        val timelineService  = mockk<TimelineService>()
        val analyticsService = mockk<AnalyticsService>()
        val profilesService  = mockk<ProfilesService>()
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"   to ISSUER,
                    "jwt.audience" to AUDIENCE,
                    "jwt.realm"    to "kdiab-bff",
                    "jwt.test"     to "true",
                    "jwt.secret"   to JWT_SECRET,
                )
            }
            application { module(timelineService, analyticsService, profilesService) }
            block(timelineService, analyticsService, profilesService)
        }
    }

    // ── Helper: build URL with timeframe params ───────────────────────────────

    private fun timelineUrl(userId: String)  = "/api/v1/users/$userId/timeline?from=$FROM&to=$TO"
    private fun hba1cUrl(userId: String)     = "/api/v1/users/$userId/analytics/hba1c?from=$FROM&to=$TO"
    private fun agpUrl(userId: String)       = "/api/v1/users/$userId/analytics/agp?from=$FROM&to=$TO"
    private fun profilesUrl(userId: String)  = "/api/v1/users/$userId/profiles/active?from=$FROM&to=$TO"

    // ── 401 — no auth token ───────────────────────────────────────────────────

    @Test
    fun `timeline - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.get(timelineUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `hba1c - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.get(hba1cUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `agp - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.get(agpUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `profiles active - 401 without auth token`() = routeTest { _, _, _ ->
        val resp = client.get(profilesUrl(SARAH_ID))
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    // ── PATIENT — reads own data (200) ────────────────────────────────────────

    @Test
    fun `timeline - 200 patient reads own data`() = routeTest { svc, _, _ ->
        coEvery { svc.getTimeline(SARAH_ID, FROM, TO, any()) } returns emptyTimeline
        val resp = client.get(timelineUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `hba1c - 200 patient reads own data`() = routeTest { _, svc, _ ->
        coEvery { svc.getHba1c(SARAH_ID, FROM, TO, any(), any()) } returns emptyHba1c
        val resp = client.get(hba1cUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `agp - 200 patient reads own data`() = routeTest { _, svc, _ ->
        coEvery { svc.getAgp(SARAH_ID, FROM, TO, any(), any()) } returns emptyAgp
        val resp = client.get(agpUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `profiles active - 200 patient reads own data`() = routeTest { _, _, svc ->
        coEvery { svc.getProfiles(SARAH_ID, FROM, TO, any()) } returns emptyProfiles
        val resp = client.get(profilesUrl(SARAH_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── PATIENT — reads another user's data (403) ─────────────────────────────

    @Test
    fun `timeline - 403 patient reads another user data`() = routeTest { _, _, _ ->
        val resp = client.get(timelineUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `hba1c - 403 patient reads another user data`() = routeTest { _, _, _ ->
        val resp = client.get(hba1cUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `agp - 403 patient reads another user data`() = routeTest { _, _, _ ->
        val resp = client.get(agpUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `profiles active - 403 patient reads another user data`() = routeTest { _, _, _ ->
        val resp = client.get(profilesUrl(MIKE_ID)) { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── DOCTOR — reads allowed patient data (200) ─────────────────────────────

    @Test
    fun `timeline - 200 doctor reads allowed patient data`() = routeTest { svc, _, _ ->
        coEvery { svc.getTimeline(SARAH_ID, FROM, TO, any()) } returns emptyTimeline
        val resp = client.get(timelineUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `hba1c - 200 doctor reads allowed patient data`() = routeTest { _, svc, _ ->
        coEvery { svc.getHba1c(SARAH_ID, FROM, TO, any(), any()) } returns emptyHba1c
        val resp = client.get(hba1cUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `agp - 200 doctor reads allowed patient data`() = routeTest { _, svc, _ ->
        coEvery { svc.getAgp(SARAH_ID, FROM, TO, any(), any()) } returns emptyAgp
        val resp = client.get(agpUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `profiles active - 200 doctor reads allowed patient data`() = routeTest { _, _, svc ->
        coEvery { svc.getProfiles(SARAH_ID, FROM, TO, any()) } returns emptyProfiles
        val resp = client.get(profilesUrl(SARAH_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── DOCTOR — reads non-allowed patient data (403) ─────────────────────────

    @Test
    fun `timeline - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _ ->
        val resp = client.get(timelineUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `hba1c - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _ ->
        val resp = client.get(hba1cUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `agp - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _ ->
        val resp = client.get(agpUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `profiles active - 403 doctor reads non-allowed patient data`() = routeTest { _, _, _ ->
        val resp = client.get(profilesUrl(MIKE_ID)) { bearerAuth(doctorToken) }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    // ── ADMIN — reads any user's data (200) ──────────────────────────────────

    @Test
    fun `timeline - 200 admin reads any user data`() = routeTest { svc, _, _ ->
        coEvery { svc.getTimeline(MIKE_ID, FROM, TO, any()) } returns emptyTimeline
        val resp = client.get(timelineUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `hba1c - 200 admin reads any user data`() = routeTest { _, svc, _ ->
        coEvery { svc.getHba1c(MIKE_ID, FROM, TO, any(), any()) } returns emptyHba1c
        val resp = client.get(hba1cUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `agp - 200 admin reads any user data`() = routeTest { _, svc, _ ->
        coEvery { svc.getAgp(MIKE_ID, FROM, TO, any(), any()) } returns emptyAgp
        val resp = client.get(agpUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    @Test
    fun `profiles active - 200 admin reads any user data`() = routeTest { _, _, svc ->
        coEvery { svc.getProfiles(MIKE_ID, FROM, TO, any()) } returns emptyProfiles
        val resp = client.get(profilesUrl(MIKE_ID)) { bearerAuth(adminToken) }
        assertEquals(HttpStatusCode.OK, resp.status)
    }

    // ── Missing query params (400) ────────────────────────────────────────────

    @Test
    fun `timeline - 400 when from param is missing`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/timeline?to=$TO") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `hba1c - 400 when to param is missing`() = routeTest { _, _, _ ->
        val resp = client.get("/api/v1/users/$SARAH_ID/analytics/hba1c?from=$FROM") { bearerAuth(sarahToken) }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }
}
