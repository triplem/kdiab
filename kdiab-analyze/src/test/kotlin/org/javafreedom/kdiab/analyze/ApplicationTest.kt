package org.javafreedom.kdiab.analyze

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.application.service.AnalyticsOperation
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageOperation
import org.javafreedom.kdiab.analyze.application.service.ProfilesOperation
import org.javafreedom.kdiab.analyze.application.service.TimelineOperation

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

class ApplicationTest {

    private fun ApplicationTestBuilder.configureTestEnv() {
        environment {
            config = MapApplicationConfig(
                "jwt.domain"   to "http://localhost:8085/realms/kdiab-analyze",
                "jwt.audience" to "analyze",
                "jwt.realm"    to "kdiab-analyze",
                "jwt.test"     to "true",
                "jwt.secret"   to "test-secret-for-unit-tests-only-hs256",
            )
        }
    }

    private fun ApplicationTestBuilder.withMockServices() {
        application {
            installMockDi(
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
            )
            module()
        }
    }

    @Test
    fun `unknown route returns 404`() = testApplication {
        configureTestEnv()
        withMockServices()
        val response = client.get("/unknown-route")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET metrics returns 200 with text content`() = testApplication {
        configureTestEnv()
        withMockServices()
        val response = client.get("/metrics") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
    }

    @Test
    fun `GET metrics without authorization returns 401`() = testApplication {
        configureTestEnv()
        withMockServices()
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
