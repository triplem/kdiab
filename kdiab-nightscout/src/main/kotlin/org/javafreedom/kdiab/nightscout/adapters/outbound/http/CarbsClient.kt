package org.javafreedom.kdiab.nightscout.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.DefaultApi
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.CreateFoodEntryRequest
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.FoodEntryResponse
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.FoodEntryStatus
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.PagedFoodResponse
import org.javafreedom.kdiab.nightscout.api.upstream.carbs.models.UpdateFoodEntryRequest
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

/**
 * Contextual serializer for [java.math.BigDecimal].
 * Reads from JSON as a number element and writes as a plain number string.
 */
private object BigDecimalSerializer : KSerializer<java.math.BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: java.math.BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): java.math.BigDecimal {
        val jsonDecoder = decoder as? JsonDecoder
        return if (jsonDecoder != null) {
            java.math.BigDecimal(jsonDecoder.decodeJsonElement().toString())
        } else {
            java.math.BigDecimal(decoder.decodeString())
        }
    }
}

private val carbsJson = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        contextual(BigDecimalSerializer)
        contextual(FoodEntryStatus::class, FoodEntryStatus.serializer())
    }
}

private const val PAGE_SIZE = 200
private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

class CarbsClient(
    httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "carbs"),
) {
    private val httpClient = HttpClient(httpClientEngine) {
        install(ContentNegotiation) { json(carbsJson) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
    }

    private fun buildApi(authorization: String, correlationId: String): DefaultApi {
        val token = authorization.removePrefix("Bearer ").trim()
        return DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClient.engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(carbsJson) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }
    }

    suspend fun listFood(
        userId: String,
        authorization: String,
        correlationId: String,
        page: Int = 0,
        size: Int = PAGE_SIZE,
    ): PagedFoodResponse {
        val api = buildApi(authorization, correlationId)
        val httpResponse = circuitBreaker.execute {
            api.listFoods(userId = userId, page = page, size = size, q = null)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "carbs",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        return httpResponse.body()
    }

    suspend fun getFood(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
    ): FoodEntryResponse? {
        // The carbs API has no GET-by-ID endpoint; fetch pages and filter locally.
        val api = buildApi(authorization, correlationId)
        var page = 0
        var totalFetched = 0
        var totalCount = Int.MAX_VALUE

        while (totalFetched < totalCount) {
            val httpResponse = circuitBreaker.execute {
                api.listFoods(userId = userId, page = page, size = PAGE_SIZE, q = null)
            }
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                throw UpstreamException(
                    service = "carbs",
                    statusCode = httpResponse.status,
                    reason = httpResponse.response.status.description,
                    url = requestUrl,
                )
            }
            val paged = httpResponse.body()
            totalCount = paged.totalCount
            val found = paged.items.firstOrNull { it.id == id }
            if (found != null) return found
            if (paged.items.isEmpty()) break
            totalFetched += paged.items.size
            page++
        }
        return null
    }

    suspend fun createFood(
        userId: String,
        authorization: String,
        correlationId: String,
        request: CreateFoodEntryRequest,
    ): FoodEntryResponse {
        val api = buildApi(authorization, correlationId)
        val httpResponse = circuitBreaker.execute {
            api.createFoodEntry(userId = userId, createFoodEntryRequest = request)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "carbs",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        logger.info { "Created food entry for nightscout userId=$userId" }
        return httpResponse.body()
    }

    suspend fun updateFood(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        request: UpdateFoodEntryRequest,
    ): FoodEntryResponse {
        val api = buildApi(authorization, correlationId)
        val httpResponse = circuitBreaker.execute {
            api.updateFoodEntry(userId = userId, foodId = id, updateFoodEntryRequest = request)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "carbs",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        logger.info { "Updated food entry foodId=$id for nightscout userId=$userId" }
        return httpResponse.body()
    }

    suspend fun deleteFood(
        userId: String,
        authorization: String,
        correlationId: String,
        id: String,
        permanent: Boolean = false,
    ) {
        val api = buildApi(authorization, correlationId)
        if (permanent) {
            val httpResponse = circuitBreaker.execute {
                api.deleteFoodEntry(userId = userId, foodId = id)
            }
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                throw UpstreamException(
                    service = "carbs",
                    statusCode = httpResponse.status,
                    reason = httpResponse.response.status.description,
                    url = requestUrl,
                )
            }
            logger.info { "Permanently deleted food entry foodId=$id for nightscout userId=$userId" }
        } else {
            val httpResponse = circuitBreaker.execute {
                api.archiveFoodEntry(userId = userId, foodId = id)
            }
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                throw UpstreamException(
                    service = "carbs",
                    statusCode = httpResponse.status,
                    reason = httpResponse.response.status.description,
                    url = requestUrl,
                )
            }
            logger.info { "Archived food entry foodId=$id for nightscout userId=$userId" }
        }
    }
}
