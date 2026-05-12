@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.calc.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.calc.api.Paths
import org.javafreedom.kdiab.calc.application.service.DoseCalculationService
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal

private val logger = KotlinLogging.logger {}

@Suppress("UnreachableCode")
fun Route.calcRoutes(doseCalculationService: DoseCalculationService) {
    authenticate("auth-jwt") {
        post<Paths.calculateDose> { params ->
            val rawUserId = params.userId
            val targetUserId = runCatching { Uuid.parse(rawUserId) }.getOrElse {
                throw BusinessValidationException("Invalid userId format: $rawUserId")
            }
            val principal = call.principal<UserPrincipal>()
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

            val requestDto = call.receive<DoseRequestDto>()
            val domainRequest = requestDto.toDomain()

            val result = doseCalculationService.calculateDose(
                userId = rawUserId,
                request = domainRequest,
                authorization = authorization,
                correlationId = correlationId,
            )
            call.respond(HttpStatusCode.OK, result.toDto())
        }
    }
}
