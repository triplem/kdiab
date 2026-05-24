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
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Food
import org.javafreedom.kdiab.nightscout.domain.model.Ns3ListResponse
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Response
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Settings
import org.javafreedom.kdiab.nightscout.domain.model.Ns3Treatment

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
        route("/api/v3/treatments") {
            get {
                val principal = call.principal<UserPrincipal>()!!
                val params = call.parseNs3SearchParams(maxLimit)
                val treatments = service.searchTreatments(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    params = params,
                )
                call.respond(Ns3ListResponse(status = 200, result = treatments))
            }
            post {
                val principal = call.principal<UserPrincipal>()!!
                val treatment = call.receive<Ns3Treatment>()
                val created = service.createTreatment(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    treatment = treatment,
                )
                call.response.header("Location", "/api/v3/treatments/${created.identifier}")
                val createResponse = Ns3Response<Ns3Treatment>(status = 201, identifier = created.identifier)
                call.respond(HttpStatusCode.Created, createResponse)
            }
            get("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val treatment = service.getTreatment(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                )
                if (treatment == null) {
                    call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3Treatment>(status = 404))
                } else {
                    call.respond(Ns3Response(status = 200, result = treatment))
                }
            }
            put("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val treatment = call.receive<Ns3Treatment>()
                val updated = service.updateTreatment(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    treatment = treatment,
                )
                call.respond(Ns3Response(status = 200, result = updated))
            }
            patch("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val treatment = call.receive<Ns3Treatment>()
                val updated = service.updateTreatment(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    treatment = treatment,
                )
                call.respond(Ns3Response(status = 200, result = updated))
            }
            delete("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val permanent = call.request.queryParameters["permanent"] == "true"
                service.deleteTreatment(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    permanent = permanent,
                )
                call.respond(Ns3Response<Unit>(status = 200))
            }
        }
        route("/api/v3/food") {
            get {
                val principal = call.principal<UserPrincipal>()!!
                val params = call.parseNs3SearchParams(maxLimit)
                val foods = service.searchFood(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    params = params,
                )
                call.respond(Ns3ListResponse(status = 200, result = foods))
            }
            post {
                val principal = call.principal<UserPrincipal>()!!
                val food = call.receive<Ns3Food>()
                val created = service.createFood(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    food = food,
                )
                call.response.header("Location", "/api/v3/food/${created.identifier}")
                val createResponse = Ns3Response<Ns3Food>(status = 201, identifier = created.identifier)
                call.respond(HttpStatusCode.Created, createResponse)
            }
            get("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val food = service.getFood(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                )
                if (food == null) {
                    call.respond(HttpStatusCode.NotFound, Ns3Response<Ns3Food>(status = 404))
                } else {
                    call.respond(Ns3Response(status = 200, result = food))
                }
            }
            put("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val food = call.receive<Ns3Food>()
                val updated = service.updateFood(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    food = food,
                )
                call.respond(Ns3Response(status = 200, result = updated))
            }
            patch("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val food = call.receive<Ns3Food>()
                val updated = service.updateFood(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    food = food,
                )
                call.respond(Ns3Response(status = 200, result = updated))
            }
            delete("/{identifier}") {
                val principal = call.principal<UserPrincipal>()!!
                val id = call.parameters["identifier"]!!
                val permanent = call.request.queryParameters["permanent"] == "true"
                service.deleteFood(
                    userId = principal.userId.toString(),
                    authorization = call.request.header("Authorization") ?: "",
                    correlationId = call.request.header("X-Correlation-ID") ?: "",
                    id = id,
                    permanent = permanent,
                )
                call.respond(Ns3Response<Unit>(status = 200))
            }
        }
        settingsRoutes(service)
    }
}

private fun Route.settingsRoutes(service: NightscoutV3Service) {
    route("/api/v3/settings") {
        get {
            val principal = call.principal<UserPrincipal>()!!
            val settings = service.getSettings(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                glucoseUnit = principal.glucoseUnit,
            )
            call.respond(Ns3Response(status = 200, result = settings))
        }
        put {
            val principal = call.principal<UserPrincipal>()!!
            val body = call.receive<Ns3Settings>()
            if (body.units.isNotEmpty() && body.units != principal.glucoseUnit) {
                call.respond(HttpStatusCode.UnprocessableEntity, Ns3Response<Ns3Settings>(status = 422))
                return@put
            }
            val settings = service.getSettings(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                glucoseUnit = principal.glucoseUnit,
            )
            call.respond(Ns3Response(status = 200, result = settings))
        }
        patch {
            val principal = call.principal<UserPrincipal>()!!
            val body = call.receive<Ns3Settings>()
            if (body.units.isNotEmpty() && body.units != principal.glucoseUnit) {
                call.respond(HttpStatusCode.UnprocessableEntity, Ns3Response<Ns3Settings>(status = 422))
                return@patch
            }
            val settings = service.getSettings(
                userId = principal.userId.toString(),
                authorization = call.request.header("Authorization") ?: "",
                correlationId = call.request.header("X-Correlation-ID") ?: "",
                glucoseUnit = principal.glucoseUnit,
            )
            call.respond(Ns3Response(status = 200, result = settings))
        }
    }
}
