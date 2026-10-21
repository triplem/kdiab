package org.javafreedom.kdiab.carbs

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
                "jwt.domain"       to "http://localhost:8081/realms/kdiab-carbs",
                "jwt.audience"     to "carbs",
                "jwt.realm"        to "kdiab-carbs",
                "jwt.test"         to "true",
                "jwt.allowTestMode" to "true",
                "jwt.secret"       to "test-secret-for-unit-tests-only-hs256",
                "app.initDatabase" to "false",
            )
        }
    }

    @Test
    fun `unknown route returns 404`() = testApplication {
        configureTestEnv()
        application { module() }
        val response = client.get("/unknown-route")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET metrics returns 200 with text content`() = testApplication {
        configureTestEnv()
        application { module() }
        val response = client.get("/metrics") {
            header(HttpHeaders.Authorization, "Bearer test-token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Plain, response.contentType()?.withoutParameters())
    }

    @Test
    fun `GET metrics without authorization returns 401`() = testApplication {
        configureTestEnv()
        application { module() }
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET healthz returns 200`() = testApplication {
        configureTestEnv()
        application { module() }
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET root returns 200 with text`() = testApplication {
        configureTestEnv()
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
