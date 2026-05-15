@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.carbs.adapters.inbound.web

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.resources.delete
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.carbs.api.Paths
import org.javafreedom.kdiab.carbs.api.models.CreateFoodEntryRequest
import org.javafreedom.kdiab.carbs.api.models.FoodEntryResponse
import org.javafreedom.kdiab.carbs.api.models.UpdateFoodEntryRequest
import org.javafreedom.kdiab.carbs.application.service.FoodEntryService
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.plugins.UserPrincipal

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 200

private val logger = KotlinLogging.logger {}

@Serializable
private data class PagedFoodResponseDto(
    val items: List<FoodEntryResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

private fun parseUuid(value: String): Uuid =
    runCatching { Uuid.parse(value) }.getOrElse {
        throw BusinessValidationException("Invalid UUID format: $value")
    }

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

private fun checkWriteAccess(principal: UserPrincipal?, targetUserId: Uuid) {
    if (principal == null || (principal.userId != targetUserId && !principal.isAdmin())) {
        logger.warn {
            "Write access denied: principalId=${principal?.userId} " +
            "roles=${principal?.roles} " +
            "allowedPatients=${principal?.allowedPatients} " +
            "targetUserId=$targetUserId"
        }
        throw AuthorizationException("Access Not Authorized")
    }
}

fun Route.foodEntryRoutes(service: FoodEntryService) {
    authenticate("auth-jwt") {
        listFoods(service)
        createFoodEntry(service)
        archiveFoodEntry(service)
        updateFoodEntry(service)
        deleteFoodEntry(service)
    }
}

private fun Route.listFoods(service: FoodEntryService) {
    get<Paths.listFoods> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkReadAccess(principal, targetUserId)

        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull()
            ?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE
        val q = call.request.queryParameters["q"]?.takeIf { it.isNotBlank() }

        val paged = service.getEntries(targetUserId, page, size, q)
        call.respond(PagedFoodResponseDto(
            items = paged.items.map { it.toApi() },
            page = paged.page,
            size = paged.size,
            totalCount = paged.totalCount,
        ))
    }
}

private fun Route.createFoodEntry(service: FoodEntryService) {
    post<Paths.createFoodEntry> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        checkWriteAccess(principal, targetUserId)

        val request = call.receive<CreateFoodEntryRequest>()
        val entry = request.toDomain(targetUserId)
        val saved = service.createEntry(entry)
        logger.info { "Created food entry ${saved.id} for user $targetUserId" }
        call.respond(HttpStatusCode.Created, saved.toApi())
    }
}

private fun Route.archiveFoodEntry(service: FoodEntryService) {
    post<Paths.archiveFoodEntry> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        val foodId = parseUuid(params.foodId)
        checkWriteAccess(principal, targetUserId)

        val archived = service.archiveEntry(foodId, targetUserId)
        logger.info { "Archived food entry $foodId for user $targetUserId" }
        call.respond(HttpStatusCode.OK, archived.toApi())
    }
}

private fun Route.updateFoodEntry(service: FoodEntryService) {
    put<Paths.updateFoodEntry> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        val foodId = parseUuid(params.foodId)
        checkWriteAccess(principal, targetUserId)

        val request = call.receive<UpdateFoodEntryRequest>()
        val updated = service.updateEntry(
            foodId, targetUserId, request.name, request.portionGrams, request.carbsPer100g
        )
        logger.info { "Updated food entry $foodId for user $targetUserId" }
        call.respond(HttpStatusCode.OK, updated.toApi())
    }
}

private fun Route.deleteFoodEntry(service: FoodEntryService) {
    delete<Paths.deleteFoodEntry> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)
        val foodId = parseUuid(params.foodId)
        checkWriteAccess(principal, targetUserId)

        service.deleteEntry(foodId, targetUserId)
        logger.info { "Deleted food entry $foodId for user $targetUserId" }
        call.respond(HttpStatusCode.NoContent)
    }
}
