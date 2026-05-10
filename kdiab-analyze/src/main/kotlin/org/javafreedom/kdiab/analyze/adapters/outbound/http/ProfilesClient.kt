package org.javafreedom.kdiab.analyze.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.analyze.api.upstream.profiles.DefaultApi
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.analyze.domain.exception.UpstreamException

private val logger = KotlinLogging.logger {}

private const val DEFAULT_PAGE_SIZE = 50

class ProfilesClient(
    private val httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
) {
    suspend fun getProfiles(userId: String, authorization: String, correlationId: String): List<Profile> {
        val token = authorization.removePrefix("Bearer ").trim()
        val api = DefaultApi(
            baseUrl = "$baseUrl/api/v1",
            httpClientEngine = httpClientEngine,
            httpClientConfig = { config ->
                config.install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                config.install(DefaultRequest) { header("X-Correlation-ID", correlationId) }
            },
        ).apply { setBearerToken(token) }

        val start = System.currentTimeMillis()
        val httpResponse = api.listProfiles(
            userId = userId,
            page = 0,
            size = DEFAULT_PAGE_SIZE,
            status = listOf("ACTIVE", "ARCHIVED"),
        )
        val ms = System.currentTimeMillis() - start
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            logger.warn {
                "Upstream profiles returned ${httpResponse.status} in ${ms}ms url=$requestUrl"
            }
            throw UpstreamException(
                service = "profiles",
                statusCode = httpResponse.status,
                message = httpResponse.response.status.description,
                responseBody = null,
                url = requestUrl,
            )
        }
        logger.info { "Fetched profiles from upstream in ${ms}ms [status=${httpResponse.status}]" }
        return httpResponse.body().items
    }
}
