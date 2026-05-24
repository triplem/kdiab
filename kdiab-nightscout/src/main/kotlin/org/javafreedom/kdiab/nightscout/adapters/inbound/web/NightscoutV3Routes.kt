@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.nightscout.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.nightscout.application.service.NightscoutV3Service
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Entry
import org.javafreedom.kdiab.nightscout.domain.model.Ns3ListResponse
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Response

fun Route.nightscoutV3Routes(service: NightscoutV3Service, maxLimit: Int) {
    authenticate("auth-jwt") {
        route("/api/v3/entries") {
            get {
                val principal = call.principal<UserPrincipal>()!!
                val params = call.parseNs3SearchParams(maxLimit)
                val entries = service.searchEntries(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    params = params,
                    glucoseUnit = principal.glucoseUnit,
                )
                call.respond(Ns3ListResponse(status = 200, result = entries))
            }
            post {
                val principal = call.principal<UserPrincipal>()!!
                val entry = call.receive<Ns3Entry>()
                val created = service.createEntry(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    entry = entry,
                    glucoseUnit = principal.glucoseUnit,
                )
                call.response.header("Location", "/api/v3/entries/${created.identifier}")
                val createResponse = Ns3Response<Ns3Entry>(status = 201, identifier = created.identifier)
                call.respond(HttpStatusCode.Created, createResponse)
            }
            get("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val entry = service.getEntry(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    glucoseUnit = principal.glucoseUnit,
                )
                if (entry == null) {
                    call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3Entry>(status = 404))
                } else {
                    call.respond(Ns3Response(status = 200, result = entry))
                }
            }
            put("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val entry = call.receive<Ns3Entry>()
                val updated = service.updateEntry(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    entry = entry,
                    glucoseUnit = principal.glucoseUnit,
                )
                call.respond(Ns3Response(status = 200, result = updated))
            }
            patch("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val entry = call.receive<Ns3Entry>()
                val updated = service.updateEntry(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    entry = entry,
                    glucoseUnit = principal.glucoseUnit,
                )
                call.respond(Ns3Response(status = 200, result = updated))
            }
            delete("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val permanent = call.request.queryParameters["permanent"] == "true"
                service.deleteEntry(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    permanent = permanent,
                )
                call.respond(Ns3Response<Unit>(status = 200))
            }
        }
    }
}
