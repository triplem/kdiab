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
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.nightscout.api.upstream.measures.DefaultApi
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.CreateMeasureRequest
import org.javafreedom.kdiab.nightscout.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val PAGE_SIZE = 200
private const val MAX_MEASURES = 50_000
private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

class MeasuresClient(
    httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "measures"),
) {
    private val httpClient = HttpClient(httpClientEngine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
    }

    suspend fun getMeasures(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<MeasureResponse> {
        val token = authorization.removePrefix("Bearer ").trim()
        val api = DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClient.engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }

        val result = mutableListOf<MeasureResponse>()
        var page = 0
        var totalCount = Long.MAX_VALUE
        var done = false

        while (result.size < totalCount && !done) {
            val httpResponse = circuitBreaker.execute {
                api.listMeasures(
                    userId = userId,
                    page = page,
                    size = PAGE_SIZE,
                    from = from,
                    to = to,
                    status = null,
                )
            }
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                throw UpstreamException(
                    service = "measures",
                    statusCode = httpResponse.status,
                    reason = httpResponse.response.status.description,
                    url = requestUrl,
                )
            }
            val paged = httpResponse.body()
            totalCount = paged.totalCount
            result.addAll(paged.items)
            if (result.size >= MAX_MEASURES) {
                logger.warn { "MAX_MEASURES limit reached for userId=$userId" }
                done = true
            } else {
                page++
                done = paged.items.isEmpty()
            }
        }

        logger.info { "Fetched ${result.size} measures for nightscout userId=$userId" }
        return result
    }

    suspend fun postMeasure(
        userId: String,
        authorization: String,
        correlationId: String,
        request: CreateMeasureRequest,
    ) {
        val token = authorization.removePrefix("Bearer ").trim()
        val api = DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClient.engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }

        val httpResponse = circuitBreaker.execute {
            api.createMeasure(userId = userId, createMeasureRequest = request)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "measures",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        logger.info { "Posted measure for nightscout userId=$userId" }
    }
}
