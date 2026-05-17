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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.api.upstream.treatments.DefaultApi
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.analyze.application.port.outbound.TreatmentsPort
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val PAGE_SIZE = 200
private const val MAX_TREATMENTS = 100_000
private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

class TreatmentsClient(
    httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "treatments"),
) : TreatmentsPort {
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

    override suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String?,
        to: String?,
    ): List<TreatmentResponse> {
        val token = authorization.removePrefix("Bearer ").trim()
        val api = buildApi(token, correlationId)
        val totalStart = System.currentTimeMillis()

        // Fetch page 0 first to learn totalCount, then fetch remaining pages in parallel.
        val firstPageStart = System.currentTimeMillis()
        val firstHttpResponse = circuitBreaker.execute {
            api.listTreatments(
                userId = userId, type = null, from = from, to = to,
                status = null, page = 0, size = PAGE_SIZE,
            )
        }
        val firstPageMs = System.currentTimeMillis() - firstPageStart
        if (!firstHttpResponse.success) {
            val requestUrl = firstHttpResponse.response.request.url.toString()
            logger.warn {
                "Upstream treatments page 0 returned ${firstHttpResponse.status} in ${firstPageMs}ms url=$requestUrl"
            }
            throw UpstreamException(
                service = "treatments",
                statusCode = firstHttpResponse.status,
                message = firstHttpResponse.response.status.description,
                responseBody = null,
                url = requestUrl,
            )
        }
        logger.info { "Fetched treatments page 0 in ${firstPageMs}ms [status=${firstHttpResponse.status}]" }
        val firstPage = firstHttpResponse.body()
        val totalCount = firstPage.totalCount

        val result = mutableListOf<TreatmentResponse>()
        result.addAll(firstPage.items)

        if (result.size < totalCount && firstPage.items.isNotEmpty()) {
            val totalPages = ((totalCount + PAGE_SIZE - 1) / PAGE_SIZE).toInt()
            val cappedTotalPages = if (totalCount > MAX_TREATMENTS) {
                logger.warn {
                    "MAX_TREATMENTS limit ($MAX_TREATMENTS) would be exceeded — capping pages for userId=$userId"
                }
                ((MAX_TREATMENTS + PAGE_SIZE - 1) / PAGE_SIZE)
            } else {
                totalPages
            }

            if (cappedTotalPages > 1) {
                val remainingPages = coroutineScope {
                    (1 until cappedTotalPages).map { pageNum ->
                        async { fetchTreatmentsPage(api, userId, pageNum, from, to) }
                    }.awaitAll()
                }
                remainingPages.forEach { result.addAll(it) }
            }
        }

        if (result.size > MAX_TREATMENTS) {
            logger.warn { "MAX_TREATMENTS limit reached — truncating at $MAX_TREATMENTS for userId=$userId" }
            val totalMs = System.currentTimeMillis() - totalStart
            logger.info { "Fetched ${MAX_TREATMENTS} treatments (truncated from ${result.size}) in ${totalMs}ms total" }
            return result.take(MAX_TREATMENTS)
        }

        val totalMs = System.currentTimeMillis() - totalStart
        val pagesFetched = ((result.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        logger.info { "Fetched ${result.size} treatments in $pagesFetched pages in ${totalMs}ms total" }
        return result
    }

    private suspend fun fetchTreatmentsPage(
        api: DefaultApi,
        userId: String,
        pageNum: Int,
        from: String?,
        to: String?,
    ): List<TreatmentResponse> {
        val pageStart = System.currentTimeMillis()
        val httpResponse = circuitBreaker.execute {
            api.listTreatments(
                userId = userId, type = null, from = from, to = to,
                status = null, page = pageNum, size = PAGE_SIZE,
            )
        }
        val pageMs = System.currentTimeMillis() - pageStart
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            logger.warn {
                "Upstream treatments page $pageNum returned ${httpResponse.status} in ${pageMs}ms url=$requestUrl"
            }
            throw UpstreamException(
                service = "treatments",
                statusCode = httpResponse.status,
                message = httpResponse.response.status.description,
                responseBody = null,
                url = requestUrl,
            )
        }
        logger.info { "Fetched treatments page $pageNum in ${pageMs}ms [status=${httpResponse.status}]" }
        return httpResponse.body().items
    }

    private fun buildApi(token: String, correlationId: String): DefaultApi =
        DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClient.engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }
}
