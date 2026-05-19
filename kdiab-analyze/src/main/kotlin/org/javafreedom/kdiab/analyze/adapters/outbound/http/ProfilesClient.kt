package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
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
import org.javafreedom.kdiab.analyze.api.upstream.profiles.DefaultApi
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.analyze.application.port.outbound.ProfilesPort
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val DEFAULT_PAGE_SIZE = 50
private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L

class ProfilesClient(
    httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
    val circuitBreaker: CircuitBreaker = CircuitBreaker(name = "profiles"),
) : ProfilesPort {
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

    override suspend fun getProfiles(userId: String, authorization: String, correlationId: String): List<Profile> {
        val token = authorization.removePrefix("Bearer ").trim()
        val api = DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClient.engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }

        val result = mutableListOf<Profile>()
        var page = 0
        val totalStart = System.currentTimeMillis()

        while (true) {
            val pageStart = System.currentTimeMillis()
            val httpResponse = circuitBreaker.execute {
                api.listProfiles(
                    userId = userId,
                    page = page,
                    size = DEFAULT_PAGE_SIZE,
                    status = listOf("ACTIVE", "ARCHIVED"),
                )
            }
            val pageMs = System.currentTimeMillis() - pageStart
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                logger.warn {
                    "Upstream profiles page $page returned ${httpResponse.status} in ${pageMs}ms url=$requestUrl"
                }
                throw UpstreamException(
                    service = "profiles",
                    statusCode = httpResponse.status,
                    message = httpResponse.response.status.description,
                    responseBody = null,
                    url = requestUrl,
                )
            }
            logger.info { "Fetched profiles page $page in ${pageMs}ms [status=${httpResponse.status}]" }
            val paged = httpResponse.body()
            result.addAll(paged.items)
            page++
            if (paged.items.isEmpty() || result.size.toLong() >= paged.totalCount) break
        }

        val totalMs = System.currentTimeMillis() - totalStart
        logger.info { "Fetched ${result.size} profiles in $page pages in ${totalMs}ms total" }
        return result
    }
}
