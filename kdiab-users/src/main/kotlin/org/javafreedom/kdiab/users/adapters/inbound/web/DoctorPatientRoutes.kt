@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.application.service.DoctorPatientService

fun Route.doctorPatientRoutes(doctorPatientService: DoctorPatientService) {
    authenticate("auth-jwt") {
        get("/users/{doctorId}/patients") {
            val principal = call.principal<UserPrincipal>()!!
            val doctorId = parseUuid(call.parameters["doctorId"]!!)
            val relations = doctorPatientService.listPatients(principal, doctorId)
            call.respond(relations.map { it.toResponse() })
        }

        post("/users/{doctorId}/patients") {
            val principal = call.principal<UserPrincipal>()!!
            val doctorId = parseUuid(call.parameters["doctorId"]!!)
            val req = call.receive<AssignPatientRequest>()
            val patientId = runCatching { parseUuid(req.patientId) }.getOrElse {
                throw BusinessValidationException("Invalid patientId: ${req.patientId}")
            }
            val relation = doctorPatientService.assignPatient(principal, doctorId, patientId)
            call.respond(HttpStatusCode.Created, relation.toResponse())
        }

        delete("/users/{doctorId}/patients/{patientId}") {
            val principal = call.principal<UserPrincipal>()!!
            val doctorId = parseUuid(call.parameters["doctorId"]!!)
            val patientId = parseUuid(call.parameters["patientId"]!!)
            doctorPatientService.removePatient(principal, doctorId, patientId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
