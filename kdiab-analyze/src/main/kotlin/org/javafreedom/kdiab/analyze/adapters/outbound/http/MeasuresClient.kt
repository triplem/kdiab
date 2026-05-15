package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.api.upstream.measures.DefaultApi
import org.javafreedom.kdiab.analyze.api.upstream.measures.models.MeasureResponse
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val PAGE_SIZE = 200    // upstream maximum (see api/openapi.yaml size.maximum)
private const val MAX_MEASURES = 100_000
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
        val totalStart = System.currentTimeMillis()

        while (result.size < totalCount && !done) {
            val pageStart = System.currentTimeMillis()
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
            val pageMs = System.currentTimeMillis() - pageStart
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                logger.warn {
                    "Upstream measures page $page returned ${httpResponse.status} in ${pageMs}ms url=$requestUrl"
                }
                throw UpstreamException(
                    service = "measures",
                    statusCode = httpResponse.status,
                    message = httpResponse.response.status.description,
                    responseBody = null,
                    url = requestUrl,
                )
            }
            logger.info { "Fetched measures page $page in ${pageMs}ms [status=${httpResponse.status}]" }
            val paged = httpResponse.body()
            totalCount = paged.totalCount
            result.addAll(paged.items)
            if (result.size >= MAX_MEASURES) {
                logger.warn { "MAX_MEASURES limit reached — truncating at $MAX_MEASURES for userId=$userId" }
                done = true
            } else {
                page++
                done = paged.items.isEmpty()   // guard against a zero-item last page
            }
        }

        val totalMs = System.currentTimeMillis() - totalStart
        logger.info { "Fetched ${result.size} measures in $page pages in ${totalMs}ms total" }
        return result
    }
}
