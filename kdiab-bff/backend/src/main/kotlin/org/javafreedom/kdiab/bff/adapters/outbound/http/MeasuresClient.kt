package org.javafreedom.kdiab.bff.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.javafreedom.kdiab.bff.domain.exception.UpstreamException

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

class MeasuresClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getMeasures(userId: String, authorization: String): List<MeasureDto> {
        logger.debug { "Fetching measures for user $userId from $baseUrl" }
        val result = mutableListOf<MeasureDto>()
        var page = 0
        val pageSize = 200   // upstream maximum
        var totalCount = Long.MAX_VALUE

        while (result.size < totalCount) {
            val response = httpClient.get("$baseUrl/api/v1/users/$userId/measures") {
                header(HttpHeaders.Authorization, authorization)
                parameter("page", page)
                parameter("size", pageSize)
            }
            if (!response.status.isSuccess()) {
                throw UpstreamException("measures", response.status.value, response.status.description)
            }
            val paged = response.body<PagedMeasureDto>()
            totalCount = paged.totalCount
            result.addAll(paged.items)
            page++
            if (paged.items.isEmpty()) break   // guard against a zero-item last page
        }

        logger.debug { "Fetched ${result.size} measures for user $userId in $page page(s)" }
        return result
    }
}
