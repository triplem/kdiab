@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Instant
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.common.plugins.parseUuid
import org.javafreedom.kdiab.measures.api.models.CreateHba1cEntryRequest
import org.javafreedom.kdiab.measures.application.service.HbA1cEntryService

private val logger = KotlinLogging.logger {}

fun Route.hba1cEntryRoutes(hbA1cEntryService: HbA1cEntryService) {
    authenticate("auth-jwt") {
        listHba1cEntries(hbA1cEntryService)
        createHba1cEntry(hbA1cEntryService)
    }
}

// UnreachableCode: detekt false positive — `?: throw` inside a lambda body causes detekt to
// flag all subsequent statements as unreachable; they are not.
@Suppress("UnreachableCode")
private fun Route.listHba1cEntries(hbA1cEntryService: HbA1cEntryService) {
    get("/users/{userId}/hba1c") {
        val principal = call.principal<UserPrincipal>()
        val rawUserId = call.parameters["userId"] ?: throw BusinessValidationException("userId is required")
        val targetUserId = parseUuid(rawUserId)

        val from = call.request.queryParameters["from"]?.let {
            runCatching { Instant.parse(it) }.getOrElse {
                throw BusinessValidationException("Invalid 'from' timestamp: $it")
            }
        }
        val to = call.request.queryParameters["to"]?.let {
            runCatching { Instant.parse(it) }.getOrElse {
                throw BusinessValidationException("Invalid 'to' timestamp: $it")
            }
        }

        val entries = hbA1cEntryService.listEntries(targetUserId, from, to, principal)
        logger.info { "Listed ${entries.size} HbA1c entries for user $targetUserId" }
        call.respond(HttpStatusCode.OK, entries.map { it.toApi() })
    }
}

// UnreachableCode: detekt false positive — `?: throw` inside a lambda body causes detekt to
// flag all subsequent statements as unreachable; they are not.
@Suppress("UnreachableCode")
private fun Route.createHba1cEntry(hbA1cEntryService: HbA1cEntryService) {
    post("/users/{userId}/hba1c") {
        val principal = call.principal<UserPrincipal>()
        val rawUserId = call.parameters["userId"] ?: throw BusinessValidationException("userId is required")
        val targetUserId = parseUuid(rawUserId)

        val request = call.receive<CreateHba1cEntryRequest>()
        val entry = request.toDomain(targetUserId)
        val saved = hbA1cEntryService.createEntry(entry, principal, targetUserId)
        logger.info { "Created HbA1c entry ${saved.id} for user $targetUserId" }
        call.response.headers.append(HttpHeaders.Location, "/api/v1/users/$targetUserId/hba1c/${saved.id}")
        call.respond(HttpStatusCode.Created, saved.toApi())
    }
}
