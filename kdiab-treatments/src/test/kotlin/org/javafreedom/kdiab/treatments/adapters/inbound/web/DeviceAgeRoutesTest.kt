@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.treatments.application.service.DeviceStatusService
import org.javafreedom.kdiab.treatments.application.service.TreatmentService
import org.javafreedom.kdiab.treatments.domain.model.TreatmentType
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.treatments.domain.repository.TreatmentRepository
import org.javafreedom.kdiab.treatments.module

// Top-level helper: installs mock DI bindings on the Application before module() runs.
// Extracted to avoid implicit-receiver ambiguity when called inside testApplication lambdas.
private fun Application.installMockDi(
    treatmentService: TreatmentService,
    deviceStatusService: DeviceStatusService,
    auditLogRepository: AuditLogRepository,
) {
    install(DI) { }
    dependencies {
        provide<TreatmentService> { treatmentService }
        provide<DeviceStatusService> { deviceStatusService }
        provide<AuditLogRepository> { auditLogRepository }
    }
}

class DeviceAgeRoutesTest {

    private companion object {
        const val JWT_SECRET = "test-secret-for-unit-tests-only"
        const val AUDIENCE   = "treatment"
        const val ISSUER     = "http://localhost:8081/realms/kdiab-treatments"
        const val SARAH_ID   = "11111111-1111-1111-1111-111111111111"
        const val MIKE_ID    = "22222222-2222-2222-2222-222222222222"

        fun token(userId: String, roles: List<String>): String = JWT.create()
            .withSubject(userId)
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("roles", roles)
            .sign(Algorithm.HMAC256(JWT_SECRET))

        val sarahToken get() = token(SARAH_ID, listOf("PATIENT"))
        val mikeToken  get() = token(MIKE_ID,  listOf("PATIENT"))
    }

    private fun deviceAgeTest(
        block: suspend ApplicationTestBuilder.(TreatmentRepository) -> Unit
    ) {
        val mockTreatmentRepo = mockk<TreatmentRepository>(relaxed = true)
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"       to ISSUER,
                    "jwt.audience"     to AUDIENCE,
                    "jwt.realm"        to "kdiab-treatments",
                    "jwt.test"         to "true",
                    "jwt.secret"       to JWT_SECRET,
                    "app.initDatabase" to "false",
                )
            }
            application {
                installMockDi(
                    TreatmentService(mockTreatmentRepo),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                )
                module()
            }
            block(mockTreatmentRepo)
        }
    }

    private val deviceTypes = setOf(
        TreatmentType.SITE_CHANGE,
        TreatmentType.INSULIN_CHANGE,
        TreatmentType.SENSOR_INSERT,
        TreatmentType.PUMP_BATTERY_CHANGE,
    )

    @Test
    fun `GET device-age returns 200 with all timestamps populated`() = deviceAgeTest { repo ->
        val catheter  = Instant.parse("2026-05-14T10:00:00Z")
        val reservoir = Instant.parse("2026-05-13T08:00:00Z")
        val sensor    = Instant.parse("2026-05-12T18:00:00Z")
        val battery   = Instant.parse("2026-05-10T14:00:00Z")
        coEvery {
            repo.findLatestTimestampsByTypes(Uuid.parse(SARAH_ID), deviceTypes)
        } returns mapOf(
            TreatmentType.SITE_CHANGE         to catheter,
            TreatmentType.INSULIN_CHANGE      to reservoir,
            TreatmentType.SENSOR_INSERT       to sensor,
            TreatmentType.PUMP_BATTERY_CHANGE to battery,
        )

        val response = client.get("/api/v1/users/$SARAH_ID/device-age") {
            header(HttpHeaders.Authorization, "Bearer $sarahToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "2026-05-14")
        assertContains(body, "2026-05-13")
        assertContains(body, "2026-05-12")
        assertContains(body, "2026-05-10")
    }

    @Test
    fun `GET device-age returns 200 with null fields when no treatments exist`() = deviceAgeTest { repo ->
        coEvery {
            repo.findLatestTimestampsByTypes(Uuid.parse(SARAH_ID), deviceTypes)
        } returns emptyMap()

        val response = client.get("/api/v1/users/$SARAH_ID/device-age") {
            header(HttpHeaders.Authorization, "Bearer $sarahToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "catheterChangedAt")
        assertContains(body, "batteryChangedAt")
        assertContains(body, "null")
    }

    @Test
    fun `GET device-age returns batteryChangedAt when PUMP_BATTERY_CHANGE exists`() = deviceAgeTest { repo ->
        val battery = Instant.parse("2026-05-10T14:00:00Z")
        coEvery {
            repo.findLatestTimestampsByTypes(Uuid.parse(SARAH_ID), deviceTypes)
        } returns mapOf(TreatmentType.PUMP_BATTERY_CHANGE to battery)

        val response = client.get("/api/v1/users/$SARAH_ID/device-age") {
            header(HttpHeaders.Authorization, "Bearer $sarahToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "batteryChangedAt")
        assertContains(body, "2026-05-10")
    }

    @Test
    fun `GET device-age returns 401 without token`() = deviceAgeTest { _ ->
        val response = client.get("/api/v1/users/$SARAH_ID/device-age")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET device-age returns 403 when patient accesses another patient`() = deviceAgeTest { _ ->
        val response = client.get("/api/v1/users/$SARAH_ID/device-age") {
            header(HttpHeaders.Authorization, "Bearer $mikeToken")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
