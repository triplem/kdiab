@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.treatments.api.Paths
import org.javafreedom.kdiab.treatments.api.models.CreateTreatmentRequest
import org.javafreedom.kdiab.treatments.api.models.BulkTreatmentRequest
import org.javafreedom.kdiab.treatments.application.service.TreatmentService
import org.javafreedom.kdiab.treatments.domain.exception.AuthorizationException
import org.javafreedom.kdiab.treatments.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.treatments.domain.model.TreatmentStatus
import org.javafreedom.kdiab.treatments.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.treatments.plugins.UserPrincipal

private val logger = KotlinLogging.logger {}

private fun parseUuid(value: String): Uuid =
    runCatching { Uuid.parse(value) }.getOrElse {
        throw BusinessValidationException("Invalid UUID format: $value")
    }

fun Route.treatmentRoutes(treatmentService: TreatmentService, auditLogRepository: AuditLogRepository) {
    authenticate("auth-jwt") {
        listTreatments(treatmentService, auditLogRepository)
        createTreatment(treatmentService, auditLogRepository)
        archiveTreatments(treatmentService, auditLogRepository)
        deleteTreatments(treatmentService, auditLogRepository)
    }
}

private fun Route.listTreatments(treatmentService: TreatmentService, auditLogRepository: AuditLogRepository) {
    get<Paths.listTreatments> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "treatments.list", auditLogRepository)

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
        val status = call.request.queryParameters["status"]?.let {
            runCatching { TreatmentStatus.valueOf(it.uppercase()) }.getOrElse {
                throw BusinessValidationException("Invalid status value: $it. Must be ACTIVE or ARCHIVED")
            }
        } ?: TreatmentStatus.ACTIVE
        val treatments = if (params.type != null) {
            val treatmentType = org.javafreedom.kdiab.treatments.domain.model.TreatmentType.valueOf(params.type.name)
            treatmentService.getTreatmentsByType(targetUserId, treatmentType, from, to, status)
        } else {
            treatmentService.getTreatments(targetUserId, from, to, status)
        }
        call.respond(treatments.map { it.toApi() })
    }
}

private fun Route.createTreatment(treatmentService: TreatmentService, auditLogRepository: AuditLogRepository) {
    post<Paths.createTreatment> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "treatments.create", auditLogRepository)

        val request = call.receive<CreateTreatmentRequest>()
        val treatment = request.toDomain(targetUserId)
        val saved = treatmentService.addTreatment(treatment)
        logger.info { "Created treatment ${saved.id} for user $targetUserId" }
        call.respond(HttpStatusCode.Created, saved.toApi())
    }
}

private fun Route.archiveTreatments(treatmentService: TreatmentService, auditLogRepository: AuditLogRepository) {
    post<Paths.archiveTreatments> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "treatments.archive", auditLogRepository)

        val request = call.receive<BulkTreatmentRequest>()
        val ids = request.treatmentIds.map { parseUuid(it) }
        treatmentService.archiveTreatments(ids, targetUserId)
        logger.info { "Archived ${ids.size} treatments for user $targetUserId" }
        call.respond(HttpStatusCode.OK)
    }
}

private fun Route.deleteTreatments(treatmentService: TreatmentService, auditLogRepository: AuditLogRepository) {
    post<Paths.deleteTreatments> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        // Patients may delete their own; doctors for assigned patients; admins for all
        checkAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "treatments.delete", auditLogRepository)

        val request = call.receive<BulkTreatmentRequest>()
        val ids = request.treatmentIds.map { parseUuid(it) }
        treatmentService.deleteTreatments(ids, targetUserId)
        logger.info { "Deleted ${ids.size} treatments for user $targetUserId" }
        call.respond(HttpStatusCode.OK)
    }
}

// ── Access control helpers ────────────────────────────────────────────────────

/** Grants access when the principal is the target user, an admin, or a doctor assigned to that patient. */
private fun checkAccess(principal: UserPrincipal?, targetUserId: Uuid) {
    if (principal == null || !principal.canAccess(targetUserId)) {
        logger.warn {
            "Access denied: principalId=${principal?.userId} " +
            "roles=${principal?.roles} " +
            "allowedPatients=${principal?.allowedPatients} " +
            "targetUserId=$targetUserId"
        }
        throw AuthorizationException("Access Not Authorized")
    }
}
