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
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.DefaultApi
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.CreateTreatmentRequest
import org.javafreedom.kdiab.nightscout.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import org.javafreedom.kdiab.common.plugins.CircuitBreakerOpenException
import org.javafreedom.kdiab.nightscout.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val PAGE_SIZE = 200
private const val MAX_TREATMENTS = 50_000
private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

class TreatmentsClient(
    httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "treatments"),
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

    suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<TreatmentResponse> {
        val token = authorization.removePrefix("Bearer ").trim()
        val api = DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClient.engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }

        val result = mutableListOf<TreatmentResponse>()
        var page = 0
        var totalCount = Long.MAX_VALUE
        var done = false

        while (result.size < totalCount && !done) {
            val httpResponse = circuitBreaker.execute {
                api.listTreatments(
                    userId = userId,
                    type = null,
                    from = from,
                    to = to,
                    status = null,
                    page = page,
                    size = PAGE_SIZE,
                )
            }
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                throw UpstreamException(
                    service = "treatments",
                    statusCode = httpResponse.status,
                    reason = httpResponse.response.status.description,
                    url = requestUrl,
                )
            }
            val paged = httpResponse.body()
            totalCount = paged.totalCount
            result.addAll(paged.items)
            if (result.size >= MAX_TREATMENTS) {
                logger.warn { "MAX_TREATMENTS limit reached for userId=$userId" }
                done = true
            } else {
                page++
                done = paged.items.isEmpty()
            }
        }

        logger.info { "Fetched ${result.size} treatments for nightscout userId=$userId" }
        return result
    }

    suspend fun postTreatment(
        userId: String,
        authorization: String,
        correlationId: String,
        request: CreateTreatmentRequest,
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
            api.createTreatment(userId = userId, createTreatmentRequest = request)
        }
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            throw UpstreamException(
                service = "treatments",
                statusCode = httpResponse.status,
                reason = httpResponse.response.status.description,
                url = requestUrl,
            )
        }
        logger.info { "Posted treatment for nightscout userId=$userId" }
    }
}
