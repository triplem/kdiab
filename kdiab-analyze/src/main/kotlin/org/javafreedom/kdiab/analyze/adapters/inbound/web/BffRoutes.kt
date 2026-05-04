@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.analyze.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import org.javafreedom.kdiab.analyze.domain.exception.AuthorizationException
import org.javafreedom.kdiab.analyze.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.analyze.plugins.UserPrincipal

private val logger = KotlinLogging.logger {}

fun Route.bffRoutes(
    timelineService: TimelineService,
    analyticsService: AnalyticsService,
    profilesService: ProfilesService,
) {
    authenticate("auth-jwt") {
        route("/api/v1/users/{userId}") {
            get("/timeline") {
                val ctx = extractContext(call)
                val (from, to) = requireDateRange(call)

                val timeline = timelineService.getTimeline(
                    userId = ctx.targetUserId.toString(),
                    from = from,
                    to = to,
                    authorization = ctx.authorization,
                    correlationId = ctx.correlationId,
                )
                call.respond(timeline.toResponse())
            }

            get("/analytics/hba1c") {
                val ctx = extractContext(call)
                val (from, to) = requireDateRange(call)

                val result = analyticsService.getHba1c(
                    userId = ctx.targetUserId.toString(),
                    from = from,
                    to = to,
                    authorization = ctx.authorization,
                    glucoseUnit = ctx.principal.glucoseUnit,
                    correlationId = ctx.correlationId,
                )
                call.respond(result.toResponse())
            }

            get("/analytics/agp") {
                val ctx = extractContext(call)
                val (from, to) = requireDateRange(call)

                val result = analyticsService.getAgp(
                    userId = ctx.targetUserId.toString(),
                    from = from,
                    to = to,
                    authorization = ctx.authorization,
                    glucoseUnit = ctx.principal.glucoseUnit,
                    correlationId = ctx.correlationId,
                )
                call.respond(result.toResponse())
            }

            get("/profiles/active") {
                val ctx = extractContext(call)
                val (from, to) = requireDateRange(call)

                val result = profilesService.getProfiles(
                    userId = ctx.targetUserId.toString(),
                    from = from,
                    to = to,
                    authorization = ctx.authorization,
                    correlationId = ctx.correlationId,
                )
                call.respond(result.toResponse())
            }
        }
    }
}

private data class RequestContext(
    val principal: UserPrincipal,
    val targetUserId: Uuid,
    val authorization: String,
    val correlationId: String,
)

private fun extractContext(call: ApplicationCall): RequestContext {
    val principal = call.principal<UserPrincipal>()
    val rawUserId = call.parameters["userId"]
        ?: throw BusinessValidationException("userId is required")
    val targetUserId = runCatching { Uuid.parse(rawUserId) }.getOrElse {
        throw BusinessValidationException("Invalid userId format: $rawUserId")
    }
    if (principal == null || !principal.canAccess(targetUserId)) {
        logger.warn {
            "Access denied: principalId=${principal?.userId} " +
            "roles=${principal?.roles} targetUserId=$targetUserId"
        }
        throw AuthorizationException("Access Not Authorized")
    }
    val authorization = call.request.headers[HttpHeaders.Authorization]
        ?: throw AuthorizationException("Missing Authorization header")
    val correlationId = call.callId ?: ""
    return RequestContext(principal!!, targetUserId, authorization, correlationId)
}

private fun requireDateRange(call: ApplicationCall): Pair<String, String> {
    val from = call.request.queryParameters["from"]
        ?: throw BusinessValidationException("Query parameter 'from' is required")
    val to = call.request.queryParameters["to"]
        ?: throw BusinessValidationException("Query parameter 'to' is required")
    val fromInstant = runCatching { kotlinx.datetime.Instant.parse(from) }.getOrElse {
        throw BusinessValidationException("Invalid 'from' date: must be ISO-8601 (e.g. 2024-01-01T00:00:00Z)")
    }
    val toInstant = runCatching { kotlinx.datetime.Instant.parse(to) }.getOrElse {
        throw BusinessValidationException("Invalid 'to' date: must be ISO-8601 (e.g. 2024-01-31T23:59:59Z)")
    }
    if (fromInstant >= toInstant) {
        throw BusinessValidationException("'from' must be before 'to'")
    }
    return from to to
}
