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
import kotlin.test.Test
import kotlin.test.assertEquals
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

class BffTimelineIntegrationTest {

    private companion object {
        const val JWT_SECRET = "test-secret-for-analyze-tests"
        const val AUDIENCE = "analyze"
        const val ISSUER = "http://localhost:8085/realms/kdiab-analyze"
        const val SARAH_ID = "11111111-1111-1111-1111-111111111111"
        const val FROM = "2024-01-01T00:00:00Z"
        const val TO = "2024-01-31T23:59:59Z"

        fun token(userId: String, roles: List<String>): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE, "measure", "profile", "treatment")
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .withClaim("glucose_unit", "mg/dL")
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val sarahToken get() = token(SARAH_ID, listOf("PATIENT"))

        // A CGM measure INSIDE the timeframe [2024-01-01, 2024-01-31]
        val measuresInRangeJson = """
            {"items":[{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","userId":"$SARAH_ID","measuredAt":"2024-01-15T10:00:00Z","createdAt":"2024-01-15T10:00:00Z","type":"CGM","source":"NIGHTSCOUT","data":{"value":120.0,"unit":"mg/dL","trend":"Flat"},"status":"ACTIVE"}],"page":0,"size":200,"totalCount":1}
        """.trimIndent()

        // A CGM measure OUTSIDE the timeframe (December 2023)
        val measuresOutOfRangeJson = """
            {"items":[{"id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","userId":"$SARAH_ID","measuredAt":"2023-12-31T10:00:00Z","createdAt":"2023-12-31T10:00:00Z","type":"CGM","source":"NIGHTSCOUT","data":{"value":100.0,"unit":"mg/dL","trend":"Flat"},"status":"ACTIVE"}],"page":0,"size":200,"totalCount":1}
        """.trimIndent()

        // Both in-range and out-of-range measures combined
        val measuresMixedJson = """
            {"items":[{"id":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","userId":"$SARAH_ID","measuredAt":"2024-01-15T10:00:00Z","createdAt":"2024-01-15T10:00:00Z","type":"CGM","source":"NIGHTSCOUT","data":{"value":120.0,"unit":"mg/dL","trend":"Flat"},"status":"ACTIVE"},{"id":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","userId":"$SARAH_ID","measuredAt":"2023-12-31T10:00:00Z","createdAt":"2023-12-31T10:00:00Z","type":"CGM","source":"NIGHTSCOUT","data":{"value":100.0,"unit":"mg/dL","trend":"Flat"},"status":"ACTIVE"}],"page":0,"size":200,"totalCount":2}
        """.trimIndent()

        val emptyPagedJson = """{"items":[],"page":0,"size":200,"totalCount":0}"""

        val treatmentInRangeJson = """
            {"items":[{"id":"cccccccc-cccc-cccc-cccc-cccccccccccc","userId":"$SARAH_ID","treatedAt":"2024-01-15T12:00:00Z","createdAt":"2024-01-15T12:00:00Z","type":"BOLUS","data":{"units":3.5},"status":"ACTIVE"}],"page":0,"size":200,"totalCount":1}
        """.trimIndent()

        val emptyTreatmentsJson = """{"items":[],"page":0,"size":200,"totalCount":0}"""

        private val lenientJson = Json { ignoreUnknownKeys = true }

        fun buildServicesWithUpstreamFailure(
            failMeasures: Boolean = false,
            failTreatments: Boolean = false,
        ): Triple<TimelineService, AnalyticsService, ProfilesService> {
            val mockEngine = MockEngine { req ->
                val path = req.url.encodedPath
                when {
                    path.contains("measures") -> if (failMeasures) {
                        respond("Internal Server Error", HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
                    } else {
                        respond(measuresInRangeJson, HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    }
                    path.contains("treatments") -> if (failTreatments) {
                        respond("Internal Server Error", HttpStatusCode.InternalServerError,
                            headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
                    } else {
                        respond(treatmentInRangeJson, HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    }
                    else -> respond(emptyPagedJson, HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
            }
            val measuresClient   = MeasuresClient(mockEngine, "http://mock-measures")
            val treatmentsClient = TreatmentsClient(mockEngine, "http://mock-treatments")
            val profilesClient   = ProfilesClient(mockEngine, "http://mock-profiles")
            return Triple(
                TimelineService(measuresClient, treatmentsClient),
                AnalyticsService(measuresClient, profilesClient),
                ProfilesService(profilesClient),
            )
        }

        fun buildServices(measuresJson: String, treatmentsJson: String): Triple<TimelineService, AnalyticsService, ProfilesService> {
            val mockEngine = MockEngine { req ->
                val path = req.url.encodedPath
                when {
                    path.contains("measures") -> respond(
                        measuresJson,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    path.contains("treatments") -> respond(
                        treatmentsJson,
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
    fun `timeline - response body contains only in-range measure`() {
        val (timelineService, analyticsService, profilesService) = buildServices(measuresMixedJson, emptyTreatmentsJson)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/timeline?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            val timeline = lenientJson.decodeFromString<TimelineResponse>(body)
            assertEquals(1, timeline.measures.size)
            assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", timeline.measures[0].id)
            assertEquals("2024-01-15T10:00:00Z", timeline.measures[0].measuredAt)
        }
    }

    @Test
    fun `timeline - response body contains treatment`() {
        val (timelineService, analyticsService, profilesService) = buildServices(emptyPagedJson, treatmentInRangeJson)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/timeline?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            val timeline = lenientJson.decodeFromString<TimelineResponse>(body)
            assertEquals(1, timeline.treatments.size)
            assertEquals("cccccccc-cccc-cccc-cccc-cccccccccccc", timeline.treatments[0].id)
            assertEquals("BOLUS", timeline.treatments[0].type)
            assertTrue(timeline.measures.isEmpty())
        }
    }

    @Test
    fun `timeline - empty measures returns empty list with 200`() {
        val (timelineService, analyticsService, profilesService) = buildServices(emptyPagedJson, emptyTreatmentsJson)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/timeline?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = resp.bodyAsText()
            val timeline = lenientJson.decodeFromString<TimelineResponse>(body)
            assertTrue(timeline.measures.isEmpty())
            assertTrue(timeline.treatments.isEmpty())
        }
    }

    @Test
    fun `timeline - measures upstream failure returns 200 with treatments and error entry`() {
        val (timelineService, analyticsService, profilesService) = buildServicesWithUpstreamFailure(failMeasures = true)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/timeline?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val timeline = lenientJson.decodeFromString<TimelineResponse>(resp.bodyAsText())
            assertTrue(timeline.measures.isEmpty())
            assertEquals(1, timeline.treatments.size)
            assertEquals(1, timeline.errors.size)
            assertTrue(timeline.errors[0].startsWith("measures:"))
        }
    }

    @Test
    fun `timeline - treatments upstream failure returns 200 with measures and error entry`() {
        val (timelineService, analyticsService, profilesService) = buildServicesWithUpstreamFailure(failTreatments = true)
        testApplication {
            setupApp(timelineService, analyticsService, profilesService)
            val resp = client.get("/api/v1/users/$SARAH_ID/timeline?from=$FROM&to=$TO") {
                bearerAuth(sarahToken)
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val timeline = lenientJson.decodeFromString<TimelineResponse>(resp.bodyAsText())
            assertEquals(1, timeline.measures.size)
            assertTrue(timeline.treatments.isEmpty())
            assertEquals(1, timeline.errors.size)
            assertTrue(timeline.errors[0].startsWith("treatments:"))
        }
    }
}
