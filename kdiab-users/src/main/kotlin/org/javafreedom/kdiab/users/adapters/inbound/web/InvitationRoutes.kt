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

fun Route.invitationRoutes(invitationService: InvitationService) {
    authenticate("auth-jwt") {
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
