@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.analyze.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.resources.get
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.analyze.api.Paths
import org.javafreedom.kdiab.analyze.adapters.outbound.http.ProfilesClient
import org.javafreedom.kdiab.analyze.adapters.outbound.http.TreatmentsClient
import org.javafreedom.kdiab.analyze.api.upstream.profiles.models.Profile
import org.javafreedom.kdiab.analyze.application.service.AnalyticsService
import org.javafreedom.kdiab.analyze.application.service.ProfilesService
import org.javafreedom.kdiab.analyze.application.service.TimelineService
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal

private val logger = KotlinLogging.logger {}

private const val DEFAULT_TIR_LOW = 70.0
private const val DEFAULT_TIR_HIGH = 180.0

fun Route.bffRoutes(
    timelineService: TimelineService,
    analyticsService: AnalyticsService,
    profilesService: ProfilesService,
    profilesClient: ProfilesClient? = null,
    treatmentsClient: TreatmentsClient? = null,
) {
    authenticate("auth-jwt") {
        get<Paths.getTimeline> { params ->
            val ctx = extractContext(call, params.userId)
            val (from, to) = validateDateRange(params.from, params.to)
            auditDoctorAccess(ctx, "analyze.timeline")

            val timeline = timelineService.getTimeline(
                userId = ctx.targetUserId.toString(),
                from = from,
                to = to,
                authorization = ctx.authorization,
                correlationId = ctx.correlationId,
            )
            call.respond(timeline.toResponse())
        }

        get<Paths.getHba1c> { params ->
            val ctx = extractContext(call, params.userId)
            val (from, to) = validateDateRange(params.from, params.to)
            auditDoctorAccess(ctx, "analyze.hba1c")

            val activeProfile = if (profilesClient != null) {
                runCatching {
                    profilesClient.getProfiles(ctx.targetUserId.toString(), ctx.authorization, ctx.correlationId)
                        .firstOrNull { it.status == Profile.Status.ACTIVE }
                }.getOrNull()
            } else null
            val tirLow = activeProfile?.analysisLow ?: DEFAULT_TIR_LOW
            val tirHigh = activeProfile?.analysisHigh ?: DEFAULT_TIR_HIGH

            val result = analyticsService.getHba1c(
                userId = ctx.targetUserId.toString(),
                from = from,
                to = to,
                authorization = ctx.authorization,
                glucoseUnit = ctx.principal.glucoseUnit,
                correlationId = ctx.correlationId,
                tirLow = tirLow,
                tirHigh = tirHigh,
            )
            call.respond(result.toResponse())
        }

        get<Paths.getAgp> { params ->
            val ctx = extractContext(call, params.userId)
            val (from, to) = validateDateRange(params.from, params.to)
            auditDoctorAccess(ctx, "analyze.agp")

            val activeProfile = if (profilesClient != null) {
                runCatching {
                    profilesClient.getProfiles(ctx.targetUserId.toString(), ctx.authorization, ctx.correlationId)
                        .firstOrNull { it.status == Profile.Status.ACTIVE }
                }.getOrNull()
            } else null
            val tirLow = activeProfile?.analysisLow ?: DEFAULT_TIR_LOW
            val tirHigh = activeProfile?.analysisHigh ?: DEFAULT_TIR_HIGH

            val result = analyticsService.getAgp(
                userId = ctx.targetUserId.toString(),
                from = from,
                to = to,
                authorization = ctx.authorization,
                glucoseUnit = ctx.principal.glucoseUnit,
                correlationId = ctx.correlationId,
                tirLow = tirLow,
                tirHigh = tirHigh,
            )
            call.respond(result.toResponse())
        }

        get<Paths.getActiveProfiles> { params ->
            val ctx = extractContext(call, params.userId)
            val (from, to) = validateDateRange(params.from, params.to)
            auditDoctorAccess(ctx, "analyze.profiles")

            val result = profilesService.getProfiles(
                userId = ctx.targetUserId.toString(),
                from = from,
                to = to,
                authorization = ctx.authorization,
                correlationId = ctx.correlationId,
            )
            call.respond(result.toResponse())
        }

        get("/users/{userId}/device-age") { handleDeviceAge(call, treatmentsClient) }

        get("/users/{userId}/device-status") { handleDeviceStatus(call, treatmentsClient) }
    }
}

private suspend fun handleDeviceAge(call: ApplicationCall, treatmentsClient: TreatmentsClient?) {
    val userId = call.parameters["userId"] ?: return call.respond(HttpStatusCode.BadRequest)
    val ctx = extractContext(call, userId)
    auditDoctorAccess(ctx, "analyze.device-age")
    val client = treatmentsClient ?: return call.respond(HttpStatusCode.ServiceUnavailable)
    val deviceAge = client.getDeviceAge(
        userId = ctx.targetUserId.toString(),
        authorization = ctx.authorization,
        correlationId = ctx.correlationId,
    )
    call.respond(deviceAge.toResponse())
}

private suspend fun handleDeviceStatus(call: ApplicationCall, treatmentsClient: TreatmentsClient?) {
    val userId = call.parameters["userId"] ?: return call.respond(HttpStatusCode.BadRequest)
    val ctx = extractContext(call, userId)
    auditDoctorAccess(ctx, "analyze.device-status")
    val client = treatmentsClient ?: return call.respond(HttpStatusCode.ServiceUnavailable)
    val deviceStatus = client.getLatestDeviceStatus(
        userId = ctx.targetUserId.toString(),
        authorization = ctx.authorization,
        correlationId = ctx.correlationId,
    )
    if (deviceStatus == null) {
        call.respond(HttpStatusCode.NoContent)
    } else {
        call.respond(deviceStatus.toResponse())
    }
}

private fun auditDoctorAccess(ctx: RequestContext, action: String) {
    if (!ctx.principal.isDoctor()) return
    logger.info {
        "AUDIT doctorId=${ctx.principal.userId} patientId=${ctx.targetUserId} action=$action"
    }
}

private data class RequestContext(
    val principal: UserPrincipal,
    val targetUserId: Uuid,
    val authorization: String,
    val correlationId: String,
)

private fun extractContext(call: ApplicationCall, rawUserId: String): RequestContext {
    val principal = call.principal<UserPrincipal>()
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
    val required = listOf("measure", "profile", "treatment")
    val missing = required.filter { it !in principal.audiences }
    if (missing.isNotEmpty()) {
        throw AuthorizationException(
            "JWT is missing required upstream audiences: ${missing.joinToString(", ")}. " +
            "Configure your Keycloak client with audience mappers for all four services.",
        )
    }
    val authorization = call.request.headers[HttpHeaders.Authorization]
        ?: throw AuthorizationException("Missing Authorization header")
    val correlationId = call.callId ?: ""
    return RequestContext(principal, targetUserId, authorization, correlationId)
}

private fun validateDateRange(from: String, to: String): Pair<String, String> {
    val fromInstant = runCatching { kotlin.time.Instant.parse(from) }.getOrElse {
        throw BusinessValidationException("Invalid 'from' date: must be ISO-8601 (e.g. 2024-01-01T00:00:00Z)")
    }
    val toInstant = runCatching { kotlin.time.Instant.parse(to) }.getOrElse {
        throw BusinessValidationException("Invalid 'to' date: must be ISO-8601 (e.g. 2024-01-31T23:59:59Z)")
    }
    if (fromInstant >= toInstant) {
        throw BusinessValidationException("'from' must be before 'to'")
    }
    return from to to
}
