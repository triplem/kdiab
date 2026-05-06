package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

@Serializable
data class MeasureDto(
    val id: String,
    val userId: String,
    val measuredAt: String,
    val type: String,
    val source: String? = null,
    val data: JsonObject,
    val status: String,
)

@Serializable
private data class PagedMeasureDto(
    val items: List<MeasureDto>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

private const val PAGE_SIZE = 200    // upstream maximum (see api/openapi.yaml size.maximum)
private const val MAX_MEASURES = 50_000 // ~120 days of CGM at 5-min intervals

class MeasuresClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getMeasures(
        userId: String,
        authorization: String,
        correlationId: String,
        from: String? = null,
        to: String? = null,
    ): List<MeasureDto> {
        val result = mutableListOf<MeasureDto>()
        var page = 0
        val pageSize = PAGE_SIZE
        var totalCount = Long.MAX_VALUE
        val totalStart = System.currentTimeMillis()

        while (result.size < totalCount) {
            check(result.size < MAX_MEASURES) {
                "Too many measures for user $userId ($totalCount total). Narrow the timeframe."
            }
            val pageStart = System.currentTimeMillis()
            val response = httpClient.get("$baseUrl/api/v1/users/$userId/measures") {
                header(HttpHeaders.Authorization, authorization)
                header("X-Correlation-ID", correlationId)
                parameter("page", page)
                parameter("size", pageSize)
                if (from != null) parameter("from", from)
                if (to != null) parameter("to", to)
            }
            val pageMs = System.currentTimeMillis() - pageStart
            if (!response.status.isSuccess()) {
                logger.warn { "Upstream measures page $page returned ${response.status.value} in ${pageMs}ms" }
                throw UpstreamException("measures", response.status.value, response.status.description)
            }
            logger.info { "Fetched measures page $page in ${pageMs}ms [status=${response.status.value}]" }
            val paged = response.body<PagedMeasureDto>()
            totalCount = paged.totalCount
            result.addAll(paged.items)
            page++
            if (paged.items.isEmpty()) break   // guard against a zero-item last page
        }

        val totalMs = System.currentTimeMillis() - totalStart
        logger.info { "Fetched ${result.size} measures in $page pages in ${totalMs}ms total" }
        return result
    }
}
