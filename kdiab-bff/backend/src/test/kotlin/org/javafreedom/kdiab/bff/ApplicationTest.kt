package org.javafreedom.kdiab.bff

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.javafreedom.kdiab.bff.application.service.AnalyticsService
import org.javafreedom.kdiab.bff.application.service.ProfilesService
import org.javafreedom.kdiab.bff.application.service.TimelineService

class ApplicationTest {

    private fun ApplicationTestBuilder.configureTestEnv() {
        environment {
            config = MapApplicationConfig(
                "jwt.domain"   to "http://localhost:8081/realms/kdiab-bff",
                "jwt.audience" to "bff",
                "jwt.realm"    to "kdiab-bff",
                "jwt.test"     to "true",
                "jwt.secret"   to "test-secret-for-unit-tests-only",
            )
        }
    }

    private fun ApplicationTestBuilder.withMockServices() {
        application {
            module(
                timelineService = mockk(),
                analyticsService = mockk(),
                profilesService = mockk(),
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
