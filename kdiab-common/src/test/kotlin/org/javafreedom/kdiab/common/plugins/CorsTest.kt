package org.javafreedom.kdiab.common.plugins

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [configureCors].
 *
 * Verifies that blank origins passed in [configureCors]'s [defaultOrigins] list are silently
 * dropped, and that valid origins are correctly allowed.
 */
class CorsTest {

    @Test
    fun `blank origin in defaultOrigins is silently dropped and valid origin is allowed`() =
        testApplication {
            environment {
                config = MapApplicationConfig()
            }
            application {
                // Pass a mix of blank and valid origins — blank must be filtered before
                // reaching allowHost(), which would throw on an empty string.
                configureCors(
                    defaultOrigins = listOf("", "http://localhost:3000"),
                    allowedMethods = listOf(HttpMethod.Get),
                )
            }

            // A request from the valid origin should receive an Access-Control-Allow-Origin header.
            val response = client.get("/") {
                header(HttpHeaders.Origin, "http://localhost:3000")
            }
            assertEquals(
                "http://localhost:3000",
                response.headers[HttpHeaders.AccessControlAllowOrigin],
                "Expected CORS header for valid origin",
            )
        }

    @Test
    fun `origin not in allowlist receives no CORS header`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            configureCors(
                defaultOrigins = listOf("http://localhost:3000"),
                allowedMethods = listOf(HttpMethod.Get),
            )
        }

        val response = client.get("/") {
            header(HttpHeaders.Origin, "http://evil.example.com")
        }
        assertNull(
            response.headers[HttpHeaders.AccessControlAllowOrigin],
            "Expected no CORS header for unknown origin",
        )
    }
}
