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
                val from = requireParam(call, "from")
                val to = requireParam(call, "to")

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
                val from = requireParam(call, "from")
                val to = requireParam(call, "to")

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
                val from = requireParam(call, "from")
                val to = requireParam(call, "to")

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
                val from = requireParam(call, "from")
                val to = requireParam(call, "to")

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

private fun requireParam(call: ApplicationCall, name: String): String =
    call.request.queryParameters[name]
        ?: throw BusinessValidationException("Query parameter '$name' is required")
