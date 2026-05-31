@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.application.service.InvitationService
import org.javafreedom.kdiab.users.domain.model.InvitationStatus

private const val DEFAULT_PAGE_SIZE = 20

fun Route.invitationRoutes(invitationService: InvitationService) {
    authenticate("auth-jwt") {
        get("/users/{doctorId}/invitations") {
            val principal = call.principal<UserPrincipal>()!!
            val doctorId = parseUuid(call.parameters["doctorId"]!!)
            val statuses = call.request.queryParameters.getAll("status")
                ?.mapNotNull { runCatching { InvitationStatus.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: setOf(InvitationStatus.PENDING)
            val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
            val result = invitationService.listDoctorInvitations(
                principal = principal,
                doctorId = doctorId,
                statuses = statuses,
                page = page,
                size = size,
            )
            call.respond(result.toPageResponse())
        }

        // GET /incoming uses a literal path segment and must be registered before any
        // /{invitationId} wildcard routes to prevent Ktor treating "incoming" as an invitationId.
        get("/users/{patientId}/invitations/incoming") {
            val principal = call.principal<UserPrincipal>()!!
            val patientId = parseUuid(call.parameters["patientId"]!!)
            val invitations = invitationService.listIncomingInvitations(
                principal = principal,
                patientId = patientId,
            )
            call.respond(invitations.map { it.toResponse() })
        }

        post("/users/{doctorId}/invitations") {
            val principal = call.principal<UserPrincipal>()!!
            val doctorId = parseUuid(call.parameters["doctorId"]!!)
            val req = call.receive<SendInvitationRequest>()
            val invitation = invitationService.sendInvitation(
                principal = principal,
                doctorId = doctorId,
                patientIdentifier = req.patientIdentifier,
                message = req.message,
            )
            call.response.header(
                HttpHeaders.Location,
                "/api/v1/users/${invitation.doctorId}/invitations/${invitation.id}",
            )
            call.respond(HttpStatusCode.Created, invitation.toResponse())
        }
    }
}
