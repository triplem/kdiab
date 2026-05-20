@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.model.AuditLog
import org.javafreedom.kdiab.common.domain.repository.AuditLogRepository

@Serializable
data class AuditLogResponse(
    val id: String,
    val doctorId: String,
    val patientId: String,
    val action: String,
    val occurredAt: String,
    val ipAddress: String?,
    val userAgent: String?,
)

fun Route.auditRoutes(auditLogRepository: AuditLogRepository) {
    authenticate("auth-jwt") {
        get("/audit") {
            val principal = call.principal<UserPrincipal>()
            if (principal?.isAdmin() != true) throw AuthorizationException("Admin access required")

            val patientIdStr = call.request.queryParameters["patientId"]
                ?: throw BusinessValidationException("patientId is required")
            val fromStr = call.request.queryParameters["from"]
                ?: throw BusinessValidationException("from is required")
            val toStr = call.request.queryParameters["to"]
                ?: throw BusinessValidationException("to is required")

            val patientId = runCatching { Uuid.parse(patientIdStr) }
                .getOrElse { throw BusinessValidationException("Invalid patientId UUID") }
            val from = runCatching { Instant.parse(fromStr) }
                .getOrElse { throw BusinessValidationException("Invalid from date (ISO-8601 required)") }
            val to = runCatching { Instant.parse(toStr) }
                .getOrElse { throw BusinessValidationException("Invalid to date (ISO-8601 required)") }

            val entries = auditLogRepository.findByPatientId(patientId, from, to)
            call.respond(entries.map {
                AuditLogResponse(
                    id = it.id.toString(),
                    doctorId = it.doctorId.toString(),
                    patientId = it.patientId.toString(),
                    action = it.action,
                    occurredAt = it.occurredAt.toString(),
                    ipAddress = it.ipAddress,
                    userAgent = it.userAgent,
                )
            })
        }
    }
}

suspend fun auditIfDoctor(
    call: ApplicationCall,
    principal: UserPrincipal?,
    patientId: Uuid,
    action: String,
    repository: AuditLogRepository,
) {
    if (principal?.isDoctor() != true) return
    val ip = call.request.local.remoteAddress
    repository.save(
        AuditLog(
            id = Uuid.random(),
            doctorId = principal.userId,
            patientId = patientId,
            action = action,
            occurredAt = Clock.System.now(),
            ipAddress = ip,
            userAgent = call.request.headers[HttpHeaders.UserAgent],
        )
    )
}

suspend fun auditDeletion(
    call: ApplicationCall,
    principal: UserPrincipal?,
    targetUserId: Uuid,
    ids: List<Uuid>,
    repository: AuditLogRepository,
    action: String,
) {
    if (principal == null) return
    val ip = call.request.local.remoteAddress
    repository.save(
        AuditLog(
            id = Uuid.random(),
            doctorId = principal.userId,
            patientId = targetUserId,
            action = action,
            occurredAt = Clock.System.now(),
            ipAddress = ip,
            userAgent = call.request.headers[HttpHeaders.UserAgent],
            detail = ids.joinToString(","),
        )
    )
}
