@file:Suppress("MatchingDeclarationName")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.profiles.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.profiles.application.service.InsulinService
import org.javafreedom.kdiab.profiles.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.profiles.plugins.ErrorResponse
import org.javafreedom.kdiab.profiles.plugins.UserPrincipal
import org.javafreedom.kdiab.profiles.api.models.Insulin as ApiInsulin
import org.javafreedom.kdiab.profiles.domain.model.Insulin as DomainInsulin

private const val INSULIN_NAME_MAX_LENGTH = 255
private const val INVALID_NAME_MSG = "Insulin name must be 1–255 characters"

private fun isValidInsulinName(name: String) = name.isNotBlank() && name.length <= INSULIN_NAME_MAX_LENGTH

@Serializable
data class InsulinRequest(val name: String)

fun Route.insulinRoutes(service: InsulinService) {
    authenticate {
        route("/api/v1/insulins") {
            get {
                val insulins = service.findAll().map { it.toApi() }
                call.respond(HttpStatusCode.OK, insulins)
            }

            post {
                val principal = call.principal<UserPrincipal>()
                if (principal == null || !principal.isAdmin()) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorResponse(HttpStatusCode.Forbidden.value, "Admin role required")
                    )
                    return@post
                }
                val request = call.receive<InsulinRequest>()
                if (!isValidInsulinName(request.name)) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(HttpStatusCode.BadRequest.value, INVALID_NAME_MSG)
                    )
                    return@post
                }
                val newInsulin = service.create(request.name).toApi()
                call.respond(HttpStatusCode.Created, newInsulin)
            }

            route("/{id}") {
                put {
                    val principal = call.principal<UserPrincipal>()
                    if (principal == null || !principal.isAdmin()) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse(HttpStatusCode.Forbidden.value, "Admin role required")
                        )
                        return@put
                    }
                    val idString = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest)
                        return@put
                    }
                    val id = runCatching { Uuid.parse(idString) }.getOrElse {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(HttpStatusCode.BadRequest.value, "Invalid UUID format")
                        )
                        return@put
                    }
                    val request = call.receive<InsulinRequest>()
                    if (!isValidInsulinName(request.name)) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(HttpStatusCode.BadRequest.value, INVALID_NAME_MSG)
                        )
                        return@put
                    }
                    val updated = service.update(id, request.name)?.toApi()
                    if (updated != null) {
                        call.respond(HttpStatusCode.OK, updated)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                delete {
                    val principal = call.principal<UserPrincipal>()
                    if (principal == null || !principal.isAdmin()) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ErrorResponse(HttpStatusCode.Forbidden.value, "Admin role required")
                        )
                        return@delete
                    }
                    val idString = call.parameters["id"] ?: run {
                        call.respond(HttpStatusCode.BadRequest)
                        return@delete
                    }
                    val id = runCatching { Uuid.parse(idString) }.getOrElse {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(HttpStatusCode.BadRequest.value, "Invalid UUID format")
                        )
                        return@delete
                    }
                    val deleted = service.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}

private fun DomainInsulin.toApi(): ApiInsulin {
    return ApiInsulin(
        id = this.id.toString(),
        name = this.name
    )
}
