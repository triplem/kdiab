package org.javafreedom.kdiab.treatments

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    private fun ApplicationTestBuilder.configureTestEnv() {
        environment {
            config = MapApplicationConfig(
                "jwt.domain"   to "http://localhost:8081/realms/kdiab-treatments",
                "jwt.audience" to "treatment",
                "jwt.realm"    to "kdiab-treatments",
                "jwt.test"     to "true",
                "jwt.secret"   to "test-secret-for-unit-tests-only",
            )
        }
    }

    @Test
    fun `unknown route returns 404`() = testApplication {
        configureTestEnv()
        application { module(initDatabase = false) }
        val response = client.get("/unknown-route")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET metrics returns 200 with text content`() = testApplication {
        configureTestEnv()
        application { module(initDatabase = false) }
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
    }
}
