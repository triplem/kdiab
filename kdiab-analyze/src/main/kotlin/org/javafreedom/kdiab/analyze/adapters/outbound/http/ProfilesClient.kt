package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import org.javafreedom.kdiab.common.plugins.CircuitBreaker
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import org.javafreedom.kdiab.analyze.domain.model.BasalSegment
import org.javafreedom.kdiab.analyze.domain.model.RatioSegment
import org.javafreedom.kdiab.analyze.domain.model.TargetSegment
import org.javafreedom.kdiab.analyze.domain.model.UpstreamProfile

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

    override suspend fun getProfiles(
        userId: String,
        authorization: String,
        correlationId: String,
    ): List<UpstreamProfile> {
        val token = authorization.removePrefix("Bearer ").trim()
        val api = DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClient.engine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
                config.install(HttpTimeout) {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            },
        ).apply { setBearerToken(token) }

        val result = mutableListOf<UpstreamProfile>()
        var page = 0
        val totalStart = System.currentTimeMillis()

        while (true) {
            val pageStart = System.currentTimeMillis()
            val httpResponse = try {
                circuitBreaker.execute {
                    api.listProfiles(
                        userId = userId,
                        page = page,
                        size = DEFAULT_PAGE_SIZE,
                        status = listOf("ACTIVE", "ARCHIVED"),
                    )
                }
            } catch (e: HttpRequestTimeoutException) {
                val pageMs = System.currentTimeMillis() - pageStart
                logger.warn { "Upstream profiles page $page timed out after ${pageMs}ms" }
                throw UpstreamException(service = "profiles", statusCode = 503, message = "Request timed out", responseBody = null, url = "$baseUrl/api/v1")
            } catch (e: java.net.ConnectException) {
                val pageMs = System.currentTimeMillis() - pageStart
                logger.warn { "Upstream profiles page $page connection refused after ${pageMs}ms" }
                throw UpstreamException(service = "profiles", statusCode = 503, message = "Connection refused", responseBody = null, url = "$baseUrl/api/v1")
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
            result.addAll(paged.items.map { it.toDomain() })
            page++
            if (paged.items.isEmpty() || result.size.toLong() >= paged.totalCount) break
        }

        val totalMs = System.currentTimeMillis() - totalStart
        logger.info { "Fetched ${result.size} profiles in $page pages in ${totalMs}ms total" }
        return result
    }
}

private fun Profile.toDomain() = UpstreamProfile(
    id = id,
    userId = userId,
    status = status.value,
    name = name,
    insulinType = insulinType,
    durationOfAction = durationOfAction,
    analysisLow = analysisLow,
    analysisHigh = analysisHigh,
    createdAt = createdAt,
    validFrom = validFrom,
    previousProfileId = previousProfileId,
    activatedAt = activatedAt,
    archivedAt = archivedAt,
    basal = basal?.map { BasalSegment(it.startTime, it.value) },
    icr = icr?.map { RatioSegment(it.startTime, it.value) },
    isf = isf?.map { RatioSegment(it.startTime, it.value) },
    targets = targets?.map { TargetSegment(it.startTime, it.low, it.high) },
)
