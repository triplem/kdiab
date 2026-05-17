package org.javafreedom.kdiab.analyze.adapters.inbound.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import org.javafreedom.kdiab.analyze.module
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BffAnalyticsIntegrationTest {

    private companion object {
        const val JWT_SECRET = "test-secret-for-analyze-tests"
        const val AUDIENCE = "analyze"
        const val ISSUER = "http://localhost:8085/realms/kdiab-analyze"
        const val SARAH_ID = "11111111-1111-1111-1111-111111111111"
        const val FROM = "2024-01-01T00:00:00Z"
        const val TO = "2024-01-31T23:59:59Z"

        // 10 CGM readings at 120.0 mg/dL, measuredAt 2024-01-15T10:0X:00Z (all hour 10 UTC)
        // HbA1c formula: (120.0 + 46.7) / 28.7 = 5.8118...
        val tenCgmReadingsJson = buildString {
            val items = (0 until 10).joinToString(",") { i ->
                val minute = i.toString().padStart(2, '0')
                """{"id":"${i.toString().repeat(8)}-${i.toString().repeat(4)}-${i.toString().repeat(4)}-${i.toString().repeat(4)}-${i.toString().repeat(12)}","userId":"$SARAH_ID","measuredAt":"2024-01-15T10:${minute}:00Z","createdAt":"2024-01-15T10:${minute}:00Z","type":"CGM","source":"NIGHTSCOUT","data":{"value":120.0,"unit":"mg/dL","trend":"Flat"},"status":"ACTIVE"}"""
            }
            append("""{"items":[$items],"page":0,"size":200,"totalCount":10}""")
        }

        val emptyPagedJson = """{"items":[],"page":0,"size":200,"totalCount":0}"""
        val emptyTreatmentsJson = """{"items":[],"page":0,"size":200,"totalCount":0}"""
        val emptyProfilesJson = """{"items":[],"page":0,"size":200,"totalCount":0}"""

        private val lenientJson = Json { ignoreUnknownKeys = true }

        // Expected HbA1c for 10 readings at 120.0 mg/dL
        const val EXPECTED_HBAIC = 5.8118
        const val HBAIC_TOLERANCE = 0.01

        fun buildServices(measuresJson: String): Triple<TimelineService, AnalyticsService, ProfilesService> {
            val mockEngine = MockEngine { req ->
                val path = req.url.encodedPath
                when {
                    path.contains("measures") -> respond(
                        measuresJson,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    path.contains("treatments") -> respond(
                        emptyTreatmentsJson,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    path.contains("profiles") -> respond(
                        emptyProfilesJson,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    else -> respond(
                        emptyPagedJson,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            val measuresClient = MeasuresClient(mockEngine, "http://mock-measures")
            val treatmentsClient = TreatmentsClient(mockEngine, "http://mock-treatments")
            val profilesClient = ProfilesClient(mockEngine, "http://mock-profiles")
            val timelineService = TimelineService(measuresClient, treatmentsClient)
            val analyticsService = AnalyticsService(measuresClient, profilesClient)
            val profilesService = ProfilesService(profilesClient)
            return Triple(timelineService, analyticsService, profilesService)
        }

        fun token(userId: String, roles: List<String>): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE, "measure", "profile", "treatment")
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .withClaim("glucose_unit", "mg/dL")
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val sarahToken get() = token(SARAH_ID, listOf("PATIENT"))
    }

    private fun ApplicationTestBuilder.setupApp(
        timelineService: TimelineService? = null,
        analyticsService: AnalyticsService? = null,
        profilesService: ProfilesService? = null,
    ) {
        environment {
            config = MapApplicationConfig(
                "jwt.domain" to ISSUER,
                "jwt.audience" to AUDIENCE,
                "jwt.realm" to "kdiab-analyze",
                "jwt.test" to "true",
                "jwt.secret" to JWT_SECRET,
            )
        }
        application { module(timelineService, analyticsService, profilesService) }
    }

    @Test
    fun `hba1c - correct calculation with known CGM data`() {
        val (timelineService, analyticsService, profilesService) = buildServices(tenCgmReadingsJson)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/analytics/hba1c?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            val result = lenientJson.decodeFromString<Hba1cResponseDto>(body)
            assertEquals(10, result.readingCount)
            assertNotNull(result.hba1c)
            assertTrue(
                abs(result.hba1c!! - EXPECTED_HBAIC) < HBAIC_TOLERANCE,
                "Expected HbA1c ~$EXPECTED_HBAIC but got ${result.hba1c}",
            )
            assertEquals(10, result.tir.inRangeCount)
        }
    }

    @Test
    fun `hba1c - empty measures returns null hba1c and readingCount 0`() {
        val (timelineService, analyticsService, profilesService) = buildServices(emptyPagedJson)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/analytics/hba1c?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            val result = lenientJson.decodeFromString<Hba1cResponseDto>(body)
            assertNull(result.hba1c)
            assertEquals(0, result.readingCount)
        }
    }

    @Test
    fun `agp - correct hourly bucket assignment`() {
        val (timelineService, analyticsService, profilesService) = buildServices(tenCgmReadingsJson)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/analytics/agp?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            val result = lenientJson.decodeFromString<AgpResponseDto>(body)
            assertEquals(24, result.hourlyData.size)

            // All 10 readings are at hour 10 UTC
            val hour10 = result.hourlyData.find { it.hour == 10 }
            assertNotNull(hour10)
            assertEquals(10, hour10.count)
            assertNotNull(hour10.median, "Median should be non-null for hour 10")
            assertNotNull(hour10.p10, "p10 should be non-null for hour 10")
            assertNotNull(hour10.p25, "p25 should be non-null for hour 10")
            assertNotNull(hour10.p75, "p75 should be non-null for hour 10")
            assertNotNull(hour10.p90, "p90 should be non-null for hour 10")

            // All other 23 buckets should have count=0
            result.hourlyData.filter { it.hour != 10 }.forEach { bucket ->
                assertEquals(0, bucket.count, "Expected 0 readings for hour ${bucket.hour}")
            }
        }
    }
}
