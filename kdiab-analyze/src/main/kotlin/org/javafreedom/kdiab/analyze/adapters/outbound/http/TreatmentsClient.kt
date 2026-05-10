package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.api.upstream.treatments.DefaultApi
import org.javafreedom.kdiab.analyze.api.upstream.treatments.models.TreatmentResponse
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val PAGE_SIZE = 200

class TreatmentsClient(
    private val httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
) {
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
            httpClientEngine = httpClientEngine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }

        val result = mutableListOf<TreatmentResponse>()
        var page = 0
        var totalCount = Long.MAX_VALUE
        val totalStart = System.currentTimeMillis()

        while (result.size < totalCount) {
            val pageStart = System.currentTimeMillis()
            val httpResponse = api.listTreatments(
                userId = userId,
                type = null,
                from = from,
                to = to,
                status = null,
                page = page,
                size = PAGE_SIZE,
            )
            val pageMs = System.currentTimeMillis() - pageStart
            if (!httpResponse.success) {
                val requestUrl = httpResponse.response.request.url.toString()
                logger.warn {
                    "Upstream treatments page $page returned ${httpResponse.status} in ${pageMs}ms url=$requestUrl"
                }
                throw UpstreamException(
                    service = "treatments",
                    statusCode = httpResponse.status,
                    message = httpResponse.response.status.description,
                    responseBody = null,
                    url = requestUrl,
                )
            }
            logger.info { "Fetched treatments page $page in ${pageMs}ms [status=${httpResponse.status}]" }
            val paged = httpResponse.body()
            totalCount = paged.totalCount
            result.addAll(paged.items)
            page++
            if (paged.items.isEmpty()) break
        }

        val totalMs = System.currentTimeMillis() - totalStart
        logger.info { "Fetched ${result.size} treatments in $page pages in ${totalMs}ms total" }
        return result
    }
}
