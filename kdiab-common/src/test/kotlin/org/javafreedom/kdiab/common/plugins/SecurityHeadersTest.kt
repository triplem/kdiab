package org.javafreedom.kdiab.common.plugins

import io.ktor.client.request.*
import io.ktor.server.config.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [configureSecurityHeaders].
 *
 * Verifies that:
 * - `Content-Security-Policy` is omitted when [includeCsp] is `false`.
 * - `Strict-Transport-Security` is absent when `server.httpsEnabled` is not `true`.
 * - Security headers are present in the normal case.
 */
class SecurityHeadersTest {

    @Test
    fun `includeCsp false does not emit Content-Security-Policy header`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            configureSecurityHeaders(includeCsp = false)
            routing { }
        }

        val response = client.get("/")
        assertNull(
            response.headers["Content-Security-Policy"],
            "CSP header must be absent when includeCsp=false",
        )
        // Other security headers should still be present.
        assertNotNull(
            response.headers["X-Content-Type-Options"],
            "X-Content-Type-Options must be present regardless of includeCsp",
        )
        assertNotNull(
            response.headers["X-Frame-Options"],
            "X-Frame-Options must be present regardless of includeCsp",
        )
    }

    @Test
    fun `HSTS header absent when httpsEnabled is false`() = testApplication {
        environment {
            config = MapApplicationConfig("server.httpsEnabled" to "false")
        }
        application {
            configureSecurityHeaders()
            routing { }
        }

        val response = client.get("/")
        assertNull(
            response.headers["Strict-Transport-Security"],
            "HSTS must not be sent over plain HTTP",
        )
    }

    @Test
    fun `HSTS header present when httpsEnabled is true`() = testApplication {
        environment {
            config = MapApplicationConfig("server.httpsEnabled" to "true")
        }
        application {
            configureSecurityHeaders()
            routing { }
        }

        val response = client.get("/")
        assertNotNull(
            response.headers["Strict-Transport-Security"],
            "HSTS must be emitted when server.httpsEnabled=true",
        )
    }

    @Test
    fun `includeCsp true emits Content-Security-Policy header`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            configureSecurityHeaders(includeCsp = true)
            routing { }
        }

        val response = client.get("/")
        assertNotNull(
            response.headers["Content-Security-Policy"],
            "CSP header must be present when includeCsp=true",
        )
    }
}
