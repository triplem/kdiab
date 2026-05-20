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
import kotlin.uuid.Uuid
import kotlin.time.Instant
import org.javafreedom.kdiab.treatments.application.service.DeviceStatusService
import org.javafreedom.kdiab.treatments.application.service.TreatmentService
import org.javafreedom.kdiab.treatments.domain.model.DeviceStatus
import org.javafreedom.kdiab.treatments.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.treatments.domain.repository.DeviceStatusRepository
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

class DeviceStatusRoutesTest {

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

    private fun deviceStatusTest(
        block: suspend ApplicationTestBuilder.(DeviceStatusRepository) -> Unit
    ) {
        val mockDeviceRepo = mockk<DeviceStatusRepository>(relaxed = true)
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "jwt.domain"   to ISSUER,
                    "jwt.audience" to AUDIENCE,
                    "jwt.realm"    to "kdiab-treatments",
                    "jwt.test"     to "true",
                    "jwt.secret"   to JWT_SECRET,
                )
            }
            application {
                installMockDi(
                    TreatmentService(mockk(relaxed = true)),
                    DeviceStatusService(mockDeviceRepo),
                    mockk(relaxed = true),
                )
                module(initDatabase = false)
            }
            block(mockDeviceRepo)
        }
    }

    private fun testDeviceStatus(userId: Uuid = Uuid.parse(SARAH_ID)) = DeviceStatus(
        id = Uuid.parse("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        userId = userId,
        recordedAt = Instant.parse("2026-05-16T10:00:00Z"),
        createdAt = Instant.parse("2026-05-16T10:00:00Z"),
        device = "AAPS 3.2.0",
        pumpName = "Dana RS",
        reservoirUnits = 142.5,
        batteryLevel = 87,
        pumpConnected = true,
    )

    @Test
    fun `GET device-status latest returns 200 with status data`() = deviceStatusTest { repo ->
        coEvery { repo.findLatestByUserId(Uuid.parse(SARAH_ID)) } returns testDeviceStatus()

        val response = client.get("/api/v1/users/$SARAH_ID/device-status/latest") {
            header(HttpHeaders.Authorization, "Bearer $sarahToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "AAPS 3.2.0")
        assertContains(body, "Dana RS")
        assertContains(body, "87")
    }

    @Test
    fun `GET device-status latest returns 404 when no status exists`() = deviceStatusTest { repo ->
        coEvery { repo.findLatestByUserId(Uuid.parse(SARAH_ID)) } returns null

        val response = client.get("/api/v1/users/$SARAH_ID/device-status/latest") {
            header(HttpHeaders.Authorization, "Bearer $sarahToken")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET device-status latest returns 401 without token`() = deviceStatusTest { _ ->
        val response = client.get("/api/v1/users/$SARAH_ID/device-status/latest")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET device-status latest returns 403 when accessing another patient`() = deviceStatusTest { _ ->
        val response = client.get("/api/v1/users/$SARAH_ID/device-status/latest") {
            header(HttpHeaders.Authorization, "Bearer $mikeToken")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `POST treatments with DEVICE_STATUS routes to device status service and returns 201`() =
        deviceStatusTest { repo ->
            coEvery { repo.save(any()) } answers { firstArg() }

            val body = """{"treatedAt":"2026-05-16T10:00:00Z","type":"DEVICE_STATUS","data":{"device":"AAPS 3.2.0","pumpName":"Dana RS","reservoirUnits":142.5,"batteryLevel":87,"pumpConnected":true}}"""
            val response = client.post("/api/v1/users/$SARAH_ID/treatments") {
                header(HttpHeaders.Authorization, "Bearer $sarahToken")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(body)
            }

            assertEquals(HttpStatusCode.Created, response.status)
        }
}
