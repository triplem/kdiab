package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProfilesClientTest {

    companion object {
        private const val BASE_URL = "http://localhost:8082"
        private const val USER_ID = "user-123"
        private const val AUTH = "Bearer test-token"
        private const val CORR = "corr-id"
        private const val PROFILE_ID = "profile-abc"

        private val activeProfileJson = """
            {
              "id": "$PROFILE_ID",
              "userId": "$USER_ID",
              "name": "Default Profile",
              "insulinType": "Novorapid",
              "durationOfAction": 240,
              "status": "ACTIVE"
            }
        """.trimIndent()

        private val pagedProfilesWithActiveJson = """
            {
              "items": [
                {
                  "id": "$PROFILE_ID",
                  "userId": "$USER_ID",
                  "name": "Default Profile",
                  "insulinType": "Novorapid",
                  "durationOfAction": 240,
                  "status": "ACTIVE"
                },
                {
                  "id": "profile-old",
                  "userId": "$USER_ID",
                  "name": "Old Profile",
                  "insulinType": "Novorapid",
                  "durationOfAction": 240,
                  "status": "ARCHIVED"
                }
              ],
              "page": 0,
              "size": 200,
              "totalCount": 2
            }
        """.trimIndent()

        private val emptyPagedProfilesJson = """
            {"items":[],"page":0,"size":200,"totalCount":0}
        """.trimIndent()
    }

    @Test
    fun `listProfiles returns all profiles on success`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(pagedProfilesWithActiveJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = ProfilesClient(engine, BASE_URL)
        val result = client.listProfiles(USER_ID, AUTH, CORR)
        assertEquals(2, result.size)
    }

    @Test
    fun `getProfile returns profile when found`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(activeProfileJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = ProfilesClient(engine, BASE_URL)
        val result = client.getProfile(USER_ID, AUTH, CORR, PROFILE_ID)
        assertNotNull(result)
        assertEquals(PROFILE_ID, result.id)
    }

    @Test
    fun `getProfile returns null when 404`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"code":404,"message":"Not Found"}"""),
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = ProfilesClient(engine, BASE_URL)
        val result = client.getProfile(USER_ID, AUTH, CORR, "nonexistent")
        assertNull(result)
    }

    @Test
    fun `getActiveProfile returns the ACTIVE profile from list`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(pagedProfilesWithActiveJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = ProfilesClient(engine, BASE_URL)
        val result = client.getActiveProfile(USER_ID, AUTH, CORR)
        assertNotNull(result)
        assertEquals(PROFILE_ID, result.id)
        assertEquals("ACTIVE", result.status.value)
    }

    @Test
    fun `getActiveProfile returns null when no ACTIVE profile exists`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(emptyPagedProfilesJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = ProfilesClient(engine, BASE_URL)
        val result = client.getActiveProfile(USER_ID, AUTH, CORR)
        assertNull(result)
    }

    @Test
    fun `archiveProfile succeeds on 204 response`() = runTest {
        var calledMethod = ""
        val engine = MockEngine { request ->
            calledMethod = request.method.value
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = ProfilesClient(engine, BASE_URL)
        // Should not throw
        client.archiveProfile(USER_ID, AUTH, CORR, PROFILE_ID)
        assertEquals("DELETE", calledMethod)
    }

    @Test
    fun `listProfiles throws UpstreamException on 5xx response`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"code":500,"message":"Internal Server Error"}"""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val cb = CircuitBreaker(name = "profiles-test", failureThreshold = 1)
        val client = ProfilesClient(engine, BASE_URL, cb)
        assertFailsWith<UpstreamException> {
            client.listProfiles(USER_ID, AUTH, CORR)
        }
    }
}
