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

import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.adapters.outbound.http.MeasuresClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import io.mockk.mockk
import org.javafreedom.kdiab.analyze.application.service.AnalyticsOperation
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageOperation
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageService
import org.javafreedom.kdiab.analyze.application.service.ProfilesOperation
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineOperation
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import org.javafreedom.kdiab.analyze.module
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

class AnalyzeAnalyticsIntegrationTest {

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
            val analyticsService = AnalyticsService(measuresClient, profilesClient, treatmentsClient)
            val profilesService = ProfilesService(profilesClient)
            return Triple(timelineService, analyticsService, profilesService)
        }

        fun token(userId: String, roles: List<String>): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE, "measure", "profile", "treatment")
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val sarahToken get() = token(SARAH_ID, listOf("PATIENT"))
    }

    private fun ApplicationTestBuilder.setupApp(
        timelineService: TimelineOperation? = null,
        analyticsService: AnalyticsOperation? = null,
        profilesService: ProfilesOperation? = null,
        deviceUsageService: DeviceUsageOperation? = mockk(),
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
        application {
            installMockDi(
                timelineService ?: mockk(relaxed = true),
                analyticsService ?: mockk(relaxed = true),
                profilesService ?: mockk(relaxed = true),
                deviceUsageService ?: mockk(relaxed = true),
            )
            module()
        }
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
    fun `agp - correct 5-minute bucket assignment`() {
        val (timelineService, analyticsService, profilesService) = buildServices(tenCgmReadingsJson)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/analytics/agp?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            val result = lenientJson.decodeFromString<AgpResponseDto>(body)
            assertEquals(288, result.bucketData.size)

            // 10 readings at 10:00–10:09 UTC split across two 5-minute buckets:
            //   minutes 600–604 → bucketIndex 120 → minuteOfDay 600 (5 readings: 00–04)
            //   minutes 605–609 → bucketIndex 121 → minuteOfDay 605 (5 readings: 05–09)
            val bucket600 = result.bucketData.find { it.minuteOfDay == 600 }
            val bucket605 = result.bucketData.find { it.minuteOfDay == 605 }
            assertNotNull(bucket600)
            assertNotNull(bucket605)
            assertEquals(5, bucket600.count)
            assertEquals(5, bucket605.count)
            assertNotNull(bucket600.median, "Median should be non-null for bucket 600")
            assertNotNull(bucket605.median, "Median should be non-null for bucket 605")
            assertEquals(10, result.totalReadingCount)

            // All other 286 buckets should have count=0
            result.bucketData.filter { it.minuteOfDay != 600 && it.minuteOfDay != 605 }.forEach { bucket ->
                assertEquals(0, bucket.count, "Expected 0 readings for minuteOfDay ${bucket.minuteOfDay}")
            }
        }
    }
}
