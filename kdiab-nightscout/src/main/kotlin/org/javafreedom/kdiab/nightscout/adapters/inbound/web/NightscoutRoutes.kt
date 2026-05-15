@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.nightscout.application.service.NightscoutService

private val logger = KotlinLogging.logger {}

private const val DEFAULT_NIGHTSCOUT_COUNT = 288

fun Route.nightscoutRoutes(service: NightscoutService) {
    authenticate("auth-jwt") {
        get("/api/v1/entries.json") {
            val ctx = extractContext(call)
            val from = call.request.queryParameters["from"]
            val to = call.request.queryParameters["to"]
            val count = call.request.queryParameters["count"]?.toIntOrNull() ?: DEFAULT_NIGHTSCOUT_COUNT

            val entries = service.getEntries(
                userId = ctx.userId,
                authorization = ctx.authorization,
                correlationId = ctx.correlationId,
                from = from,
                to = to,
                count = count,
            )
            call.respond(HttpStatusCode.OK, entries)
        }

        get("/api/v1/treatments.json") {
            val ctx = extractContext(call)
            val from = call.request.queryParameters["from"]
            val to = call.request.queryParameters["to"]
            val count = call.request.queryParameters["count"]?.toIntOrNull() ?: DEFAULT_NIGHTSCOUT_COUNT

            val treatments = service.getTreatments(
                userId = ctx.userId,
                authorization = ctx.authorization,
                correlationId = ctx.correlationId,
                from = from,
                to = to,
                count = count,
            )
            call.respond(HttpStatusCode.OK, treatments)
        }
    }
}

private data class RequestContext(
    val userId: String,
    val authorization: String,
    val correlationId: String,
)

private fun extractContext(call: ApplicationCall): RequestContext {
    val principal = call.principal<UserPrincipal>()
        ?: throw AuthorizationException("Missing authentication")
    val authorization = call.request.headers[HttpHeaders.Authorization]
        ?: throw AuthorizationException("Missing Authorization header")
    val correlationId = call.callId ?: ""
    return RequestContext(
        userId = principal.userId.toString(),
        authorization = authorization,
        correlationId = correlationId,
    )
}
