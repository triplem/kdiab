package org.javafreedom.kdiab.calc.adapters.outbound.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.engine.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.javafreedom.kdiab.calc.api.upstream.profiles.DefaultApi
import org.javafreedom.kdiab.calc.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.calc.domain.exception.UpstreamException
import org.javafreedom.kdiab.calc.domain.model.ActiveProfile
import org.javafreedom.kdiab.calc.domain.model.GlucoseTarget
import org.javafreedom.kdiab.calc.domain.model.IcrRatio
import org.javafreedom.kdiab.calc.domain.model.IsfRatio
import org.javafreedom.kdiab.calc.domain.repository.ProfilesPort

private val logger = KotlinLogging.logger {}

private const val DEFAULT_PAGE_SIZE = 50

class ProfilesClient(
    private val httpClientEngine: HttpClientEngine,
    private val baseUrl: String,
) : ProfilesPort {
    override suspend fun getActiveProfile(userId: String, authorization: String, correlationId: String): ActiveProfile? {
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
        val httpResponse = api.listProfiles(userId = userId, page = 0, size = DEFAULT_PAGE_SIZE, status = null)
        val ms = System.currentTimeMillis() - start
        if (!httpResponse.success) {
            val requestUrl = httpResponse.response.request.url.toString()
            logger.warn {
                "Upstream profiles returned ${httpResponse.status} in ${ms}ms" +
                    " url=$requestUrl"
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
        val paged = httpResponse.body()
        return paged.items.firstOrNull { it.status == Profile.Status.ACTIVE }?.toDomain()
    }
}

private fun Profile.toDomain() = ActiveProfile(
    id = id,
    timeZone = timeZone,
    isf = isf.orEmpty().map { IsfRatio(it.startTime, it.`value`) },
    icr = icr.orEmpty().map { IcrRatio(it.startTime, it.`value`) },
    targets = targets.orEmpty().map { GlucoseTarget(it.startTime, it.low, it.high) },
)
