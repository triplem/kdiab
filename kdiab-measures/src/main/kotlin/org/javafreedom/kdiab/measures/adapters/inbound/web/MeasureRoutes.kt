@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

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
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.measures.api.Paths
import org.javafreedom.kdiab.measures.api.models.CreateMeasureRequest
import org.javafreedom.kdiab.measures.api.models.BulkMeasureRequest
import org.javafreedom.kdiab.measures.api.models.MeasureResponse
import org.javafreedom.kdiab.measures.application.service.MeasureService
import org.javafreedom.kdiab.measures.domain.exception.AuthorizationException
import org.javafreedom.kdiab.measures.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.measures.domain.repository.AuditLogRepository
import org.javafreedom.kdiab.measures.plugins.UserPrincipal

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 200

@Serializable
private data class PagedMeasureResponse(
    val items: List<MeasureResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

private val logger = KotlinLogging.logger {}

private fun parseUuid(value: String): Uuid =
    runCatching { Uuid.parse(value) }.getOrElse {
        throw BusinessValidationException("Invalid UUID format: $value")
    }

fun Route.measureRoutes(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    authenticate("auth-jwt") {
        listMeasures(measureService, auditLogRepository)
        createMeasure(measureService, auditLogRepository)
        archiveMeasures(measureService, auditLogRepository)
        deleteMeasures(measureService, auditLogRepository)
    }
}

private fun Route.listMeasures(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    get<Paths.listMeasures> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkReadAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "measures.list", auditLogRepository)

        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull()
            ?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
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

        val glucoseUnit = principal?.glucoseUnit ?: "mg/dL"
        val weightUnit = principal?.weightUnit ?: "kg"
        val paged = measureService.getMeasures(targetUserId, page, size, from, to)
        call.respond(PagedMeasureResponse(
            items = paged.items.map { it.toApi(glucoseUnit, weightUnit) },
            page = paged.page,
            size = paged.size,
            totalCount = paged.totalCount,
        ))
    }
}

private fun Route.createMeasure(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    post<Paths.createMeasure> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkReadAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "measures.create", auditLogRepository)

        val glucoseUnit = principal?.glucoseUnit ?: "mg/dL"
        val weightUnit = principal?.weightUnit ?: "kg"
        val request = call.receive<CreateMeasureRequest>()
        val measure = request.toDomain(targetUserId)
        val saved = measureService.addMeasure(measure)
        logger.info { "Created measure ${saved.id} for user $targetUserId" }
        call.respond(HttpStatusCode.Created, saved.toApi(glucoseUnit, weightUnit))
    }
}

private fun Route.archiveMeasures(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    post<Paths.archiveMeasures> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkReadAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "measures.archive", auditLogRepository)

        val request = call.receive<BulkMeasureRequest>()
        val ids = request.measureIds.map { parseUuid(it) }
        measureService.archiveMeasures(ids, targetUserId)
        logger.info { "Archived ${ids.size} measures for user $targetUserId" }
        call.respond(HttpStatusCode.OK)
    }
}

private fun Route.deleteMeasures(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    post<Paths.deleteMeasures> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        if (principal == null || (!principal.isAdmin() && !principal.isDoctor())) {
            logger.warn { "Delete denied: principalId=${principal?.userId} roles=${principal?.roles}" }
            throw AuthorizationException("Only doctors and admins can permanently delete measures")
        }
        checkReadAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "measures.delete", auditLogRepository)

        val request = call.receive<BulkMeasureRequest>()
        val ids = request.measureIds.map { parseUuid(it) }
        measureService.deleteMeasures(ids, targetUserId)
        logger.info { "Deleted ${ids.size} measures for user $targetUserId" }
        call.respond(HttpStatusCode.OK)
    }
}

// ── Access control helpers ────────────────────────────────────────────────────

private fun checkReadAccess(principal: UserPrincipal?, targetUserId: Uuid) {
    if (principal == null || !principal.canAccess(targetUserId)) {
        logger.warn {
            "Read access denied: principalId=${principal?.userId} " +
            "roles=${principal?.roles} " +
            "allowedPatients=${principal?.allowedPatients} " +
            "targetUserId=$targetUserId"
        }
        throw AuthorizationException("Access Not Authorized")
    }
}
