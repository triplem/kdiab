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

class CarbsClientTest {

    companion object {
        private const val BASE_URL = "http://localhost:8085"
        private const val USER_ID = "user-123"
        private const val AUTH = "Bearer test-token"
        private const val CORR = "corr-id"
        private const val FOOD_ID = "food-abc"

        private val pagedFoodResponseJson = """
            {
              "items": [
                {
                  "id": "$FOOD_ID",
                  "userId": "$USER_ID",
                  "name": "Apple",
                  "portionGrams": 150,
                  "carbsPer100g": 14,
                  "carbsForPortion": 21,
                  "status": "ACTIVE",
                  "createdAt": "2024-01-01T00:00:00Z",
                  "updatedAt": "2024-01-01T00:00:00Z"
                }
              ],
              "page": 0,
              "size": 200,
              "totalCount": 1
            }
        """.trimIndent()

        private val foodEntryResponseJson = """
            {
              "id": "$FOOD_ID",
              "userId": "$USER_ID",
              "name": "Apple",
              "portionGrams": 150,
              "carbsPer100g": 14,
              "carbsForPortion": 21,
              "status": "ACTIVE",
              "createdAt": "2024-01-01T00:00:00Z",
              "updatedAt": "2024-01-01T00:00:00Z"
            }
        """.trimIndent()
    }

    @Test
    fun `listFood returns paged response on success`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(pagedFoodResponseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = CarbsClient(engine, BASE_URL)
        val result = client.listFood(USER_ID, AUTH, CORR)
        assertEquals(1, result.items.size)
        assertEquals(FOOD_ID, result.items.first().id)
    }

    @Test
    fun `getFood returns matching item when found`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(pagedFoodResponseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = CarbsClient(engine, BASE_URL)
        val result = client.getFood(USER_ID, AUTH, CORR, FOOD_ID)
        assertNotNull(result)
        assertEquals(FOOD_ID, result.id)
    }

    @Test
    fun `getFood returns null when not found in any page`() = runTest {
        val emptyPageJson = """{"items":[],"page":0,"size":200,"totalCount":0}"""
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(emptyPageJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = CarbsClient(engine, BASE_URL)
        val result = client.getFood(USER_ID, AUTH, CORR, "nonexistent-id")
        assertNull(result)
    }

    @Test
    fun `createFood returns created food entry on success`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(foodEntryResponseJson),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = CarbsClient(engine, BASE_URL)
        val request = org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.CreateFoodEntryRequest(
            name = "Apple",
            portionGrams = java.math.BigDecimal("150"),
            carbsPer100g = java.math.BigDecimal("14"),
        )
        val result = client.createFood(USER_ID, AUTH, CORR, request)
        assertEquals(FOOD_ID, result.id)
        assertEquals("Apple", result.name)
    }

    @Test
    fun `updateFood returns updated food entry on success`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel(foodEntryResponseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = CarbsClient(engine, BASE_URL)
        val request = org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.UpdateFoodEntryRequest(
            name = "Apple",
            portionGrams = java.math.BigDecimal("150"),
            carbsPer100g = java.math.BigDecimal("14"),
        )
        val result = client.updateFood(USER_ID, AUTH, CORR, FOOD_ID, request)
        assertEquals(FOOD_ID, result.id)
    }

    @Test
    fun `deleteFood with permanent=false calls archive endpoint`() = runTest {
        var calledPath = ""
        val engine = MockEngine { request ->
            calledPath = request.url.encodedPath
            respond(
                content = ByteReadChannel(foodEntryResponseJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = CarbsClient(engine, BASE_URL)
        client.deleteFood(USER_ID, AUTH, CORR, FOOD_ID, permanent = false)
        assertEquals("/api/v1/users/$USER_ID/foods/$FOOD_ID/archive", calledPath)
    }

    @Test
    fun `deleteFood with permanent=true calls delete endpoint`() = runTest {
        var calledPath = ""
        var calledMethod = ""
        val engine = MockEngine { request ->
            calledPath = request.url.encodedPath
            calledMethod = request.method.value
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = CarbsClient(engine, BASE_URL)
        client.deleteFood(USER_ID, AUTH, CORR, FOOD_ID, permanent = true)
        assertEquals("/api/v1/users/$USER_ID/foods/$FOOD_ID", calledPath)
        assertEquals("DELETE", calledMethod)
    }

    @Test
    fun `listFood throws UpstreamException on 5xx response`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = ByteReadChannel("""{"code":500,"message":"Internal Server Error"}"""),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        // Use circuit breaker with low threshold to test it trips quickly
        val cb = CircuitBreaker(name = "carbs-test", failureThreshold = 1)
        val client = CarbsClient(engine, BASE_URL, cb)
        assertFailsWith<UpstreamException> {
            client.listFood(USER_ID, AUTH, CORR)
        }
    }
}
