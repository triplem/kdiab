package org.javafreedom.kdiab.analyze

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.javafreedom.kdiab.analyze.application.service.AnalyticsOperation
import org.javafreedom.kdiab.analyze.application.service.DeviceUsageOperation
import org.javafreedom.kdiab.analyze.application.service.ProfilesOperation
import org.javafreedom.kdiab.analyze.application.service.TimelineOperation

class ApplicationTest {

    private fun ApplicationTestBuilder.configureTestEnv() {
        environment {
            config = MapApplicationConfig(
                "jwt.domain"   to "http://localhost:8085/realms/kdiab-analyze",
                "jwt.audience" to "analyze",
                "jwt.realm"    to "kdiab-analyze",
                "jwt.test"     to "true",
                "jwt.secret"   to "test-secret-for-unit-tests-only",
            )
        }
    }

    private fun ApplicationTestBuilder.withMockServices() {
        application {
            module(
                timelineService = mockk<TimelineOperation>(),
                analyticsService = mockk<AnalyticsOperation>(),
                profilesService = mockk<ProfilesOperation>(),
                deviceUsageService = mockk<DeviceUsageOperation>(),
            )
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
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
    }
}
