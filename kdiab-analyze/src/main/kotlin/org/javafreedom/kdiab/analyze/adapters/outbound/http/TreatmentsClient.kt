package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val PAGE_SIZE = 200
private const val LOG_BODY_MAX_CHARS = 200

@Serializable
data class TreatmentDto(
    val id: String,
    val userId: String,
    val treatedAt: String,
    val type: String,
    val notes: String? = null,
    val data: JsonObject,
)

@Serializable
private data class PagedTreatmentDto(
    val items: List<TreatmentDto>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

class TreatmentsClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getTreatments(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<TreatmentDto> {
        val result = mutableListOf<TreatmentDto>()
        var page = 0
        var totalCount = Long.MAX_VALUE
        val totalStart = System.currentTimeMillis()

        while (result.size < totalCount) {
            val pageStart = System.currentTimeMillis()
            val response = httpClient.get("$baseUrl/api/v1/users/$userId/treatments") {
                header(HttpHeaders.Authorization, authorization)
                header("X-Correlation-ID", correlationId)
                parameter("page", page)
                parameter("size", PAGE_SIZE)
                if (from != null) parameter("from", from)
                if (to != null) parameter("to", to)
            }
            val pageMs = System.currentTimeMillis() - pageStart
            if (!response.status.isSuccess()) {
                val body = runCatching { response.bodyAsText() }.getOrNull()
                val requestUrl = response.request.url.toString()
                logger.warn {
                    "Upstream treatments page $page returned ${response.status.value} in ${pageMs}ms" +
                        " url=$requestUrl body=${body?.take(LOG_BODY_MAX_CHARS)}"
                }
                throw UpstreamException(
                    "treatments",
                    response.status.value,
                    response.status.description,
                    responseBody = body,
                    url = requestUrl,
                )
            }
            logger.info { "Fetched treatments page $page in ${pageMs}ms [status=${response.status.value}]" }
            val paged = response.body<PagedTreatmentDto>()
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
