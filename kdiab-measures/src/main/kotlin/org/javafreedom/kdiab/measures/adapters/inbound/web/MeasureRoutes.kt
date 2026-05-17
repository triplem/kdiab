@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.measures.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.javafreedom.kdiab.measures.api.Paths
import org.javafreedom.kdiab.measures.api.models.CreateMeasureRequest
import org.javafreedom.kdiab.measures.api.models.BulkMeasureRequest
import org.javafreedom.kdiab.measures.api.models.MeasureResponse
import org.javafreedom.kdiab.measures.api.models.UpdateMeasureRequest
import org.javafreedom.kdiab.measures.application.service.MeasureService
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.common.plugins.checkReadAccess
import org.javafreedom.kdiab.common.plugins.checkWriteAccess
import org.javafreedom.kdiab.common.plugins.parseUuid
import org.javafreedom.kdiab.measures.domain.model.MeasureStatus
import org.javafreedom.kdiab.measures.domain.repository.AuditLogRepository

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

fun Route.measureRoutes(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    authenticate("auth-jwt") {
        listMeasures(measureService, auditLogRepository)
        createMeasure(measureService, auditLogRepository)
        updateMeasure(measureService, auditLogRepository)
        archiveMeasures(measureService, auditLogRepository)
        unarchiveMeasures(measureService, auditLogRepository)
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

        val status = call.request.queryParameters["status"]?.let {
            runCatching { MeasureStatus.valueOf(it) }.getOrElse {
                throw BusinessValidationException("Invalid status value: $it. Must be ACTIVE or ARCHIVED")
            }
        } ?: MeasureStatus.ACTIVE
        val glucoseUnit = principal?.glucoseUnit ?: "mg/dL"
        val weightUnit = principal?.weightUnit ?: "kg"
        val paged = measureService.getMeasures(targetUserId, page, size, from, to, status)
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
        checkWriteAccess(principal, targetUserId)
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

private fun Route.updateMeasure(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    put<Paths.updateMeasure> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        val measureId = parseUuid(params.measureId)
        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "measures.update", auditLogRepository)

        val glucoseUnit = principal?.glucoseUnit ?: "mg/dL"
        val weightUnit = principal?.weightUnit ?: "kg"
        val request = call.receive<UpdateMeasureRequest>()
        val measuredAt = runCatching { Instant.parse(request.measuredAt) }.getOrElse {
            throw BusinessValidationException("Invalid measuredAt timestamp: '${request.measuredAt}'")
        }
        val data = Json.parseToJsonElement(request.`data`.toString()).jsonObject
        val updated = measureService.updateMeasure(measureId, targetUserId, measuredAt, data)
        logger.info { "Updated measure $measureId for user $targetUserId" }
        call.respond(HttpStatusCode.OK, updated.toApi(glucoseUnit, weightUnit))
    }
}

private fun Route.archiveMeasures(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    post<Paths.archiveMeasures> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "measures.archive", auditLogRepository)

        val request = call.receive<BulkMeasureRequest>()
        val ids = request.measureIds.map { parseUuid(it) }
        measureService.archiveMeasures(ids, targetUserId)
        logger.info { "Archived ${ids.size} measures for user $targetUserId" }
        call.respond(HttpStatusCode.OK)
    }
}

private fun Route.unarchiveMeasures(measureService: MeasureService, auditLogRepository: AuditLogRepository) {
    post<Paths.unarchiveMeasures> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "measures.unarchive", auditLogRepository)

        val request = call.receive<BulkMeasureRequest>()
        val ids = request.measureIds.map { parseUuid(it) }
        measureService.unarchiveMeasures(ids, targetUserId)
        logger.info { "Unarchived ${ids.size} measures for user $targetUserId" }
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
        checkWriteAccess(principal, targetUserId)

        val request = call.receive<BulkMeasureRequest>()
        val ids = request.measureIds.map { parseUuid(it) }
        auditDeletion(call, principal, targetUserId, ids, auditLogRepository)
        measureService.deleteMeasures(ids, targetUserId)
        logger.info { "Deleted ${ids.size} measures for user $targetUserId" }
        call.respond(HttpStatusCode.OK)
    }
}

