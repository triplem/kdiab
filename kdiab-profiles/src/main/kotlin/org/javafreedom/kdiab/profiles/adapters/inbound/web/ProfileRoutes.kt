package org.javafreedom.kdiab.profiles.adapters.inbound.web

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.profiles.api.models.CreateProfileRequest
import org.javafreedom.kdiab.profiles.api.models.Profile as ApiProfile
import org.javafreedom.kdiab.profiles.api.models.RejectProfileRequest
import org.javafreedom.kdiab.profiles.application.service.ProfileService
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.profiles.domain.model.ProfileStatus
import org.javafreedom.kdiab.profiles.domain.repository.AuditLogRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private const val DEFAULT_PAGE_SIZE = 50
private const val MAX_PAGE_SIZE = 200

@Serializable
private data class PagedProfilesResponse(
    val items: List<ApiProfile>,
    val page: Int,
    val size: Int,
    val totalCount: Long,
)

private val logger = KotlinLogging.logger {}

private fun parseUuid(value: String): Uuid =
    runCatching { Uuid.parse(value) }.getOrElse {
        throw BusinessValidationException("Invalid UUID format: $value")
    }

fun Route.profileRoutes(profileService: ProfileService, auditLogRepository: AuditLogRepository) {

    authenticate("auth-jwt") {
        listProfiles(profileService, auditLogRepository)
        getProfileHistory(profileService, auditLogRepository)
        getProfile(profileService, auditLogRepository)
        createProfile(profileService, auditLogRepository)
        updateProfile(profileService, auditLogRepository)
        activateProfile(profileService, auditLogRepository)
        acceptProposedProfile(profileService, auditLogRepository)
        rejectProposedProfile(profileService, auditLogRepository)
        deleteSegment(profileService, auditLogRepository)
        deleteProfile(profileService, auditLogRepository)
        deleteAllProfiles(profileService, auditLogRepository)
    }
}

private fun Route.listProfiles(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    get<org.javafreedom.kdiab.profiles.api.Paths.listProfiles> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkReadAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.list", auditLogRepository)

        val page = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val size = call.request.queryParameters["size"]?.toIntOrNull()
            ?.coerceIn(1, MAX_PAGE_SIZE) ?: DEFAULT_PAGE_SIZE

        val paged = profileService.getProfiles(targetUserId, page, size)
        call.respond(PagedProfilesResponse(
            items = paged.items.map { it.toApi() },
            page = paged.page,
            size = paged.size,
            totalCount = paged.totalCount,
        ))
    }
}

private fun Route.getProfileHistory(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    get<org.javafreedom.kdiab.profiles.api.Paths.getProfileHistory> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkReadAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.history", auditLogRepository)

        val from = kotlin.time.Instant.parse(params.from)
        val to = kotlin.time.Instant.parse(params.to)

        val history = profileService.getHistory(targetUserId, from, to)
        call.respond(history.map { it.toApi() })
    }
}

private fun Route.getProfile(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    get<org.javafreedom.kdiab.profiles.api.Paths.getProfile> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkReadAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.get", auditLogRepository)

        val profileId = parseUuid(params.profileId)
        val profile = profileService.getProfile(profileId)

        if (profile != null) {
            if (profile.userId != targetUserId) {
                throw ResourceNotFoundException("Profile not found")
            }
            call.respond(profile.toApi())
        } else {
            throw ResourceNotFoundException("Profile not found")
        }
    }
}

private fun Route.createProfile(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    post<org.javafreedom.kdiab.profiles.api.Paths.createProfile> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkReadAccess(principal, targetUserId)

        // Doctor role takes precedence: allowedPatients is always enforced even for admin-doctors.
        val status = when {
            principal?.userId == targetUserId -> {
                // User creating for themselves — standard write access
                checkWriteAccess(principal, targetUserId)
                ProfileStatus.DRAFT
            }
            principal?.isDoctor() == true -> {
                // Doctor acting for another user — must be in allowedPatients regardless of other roles
                if (!principal.allowedPatients.contains(targetUserId)) {
                    throw AuthorizationException("Write Access Denied")
                }
                ProfileStatus.PROPOSED
            }
            principal?.isAdmin() == true -> {
                // Pure admin (not a doctor) acting for a user — allowed, creates DRAFT
                ProfileStatus.DRAFT
            }
            else -> {
                checkWriteAccess(principal, targetUserId)
                ProfileStatus.DRAFT
            }
        }

        auditIfDoctor(call, principal, targetUserId, "profiles.create", auditLogRepository)

        val request = call.receive<CreateProfileRequest>()
        val createdBy = if (status == ProfileStatus.PROPOSED) {
            principal?.userId
        } else null
        val domainProfile = request.toDomain(targetUserId, status, createdBy)
        val created = profileService.createProfile(domainProfile)
        logger.info { "Created profile ${created.id} for user $targetUserId with status $status" }
        call.respond(HttpStatusCode.Created, created.toApi())
    }
}

private fun Route.updateProfile(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    put<org.javafreedom.kdiab.profiles.api.Paths.updateProfile> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.update", auditLogRepository)

        val profileId = parseUuid(params.profileId)
        val request = call.receive<org.javafreedom.kdiab.profiles.api.models.Profile>()
        val domainProfile = request.toDomain()

        // Ensure both the ID and the userId in the body match the path parameters
        if (domainProfile.id != profileId) {
            throw BusinessValidationException("Profile ID mismatch")
        }
        if (domainProfile.userId != targetUserId) {
            throw BusinessValidationException("Profile userId does not match URL")
        }

        val updated = profileService.updateProfile(domainProfile)
        logger.info { "Updated profile ${domainProfile.id} for user $targetUserId" }
        call.respond(updated.toApi())
    }
}

private fun Route.activateProfile(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    post<org.javafreedom.kdiab.profiles.api.Paths.activateProfile> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.activate", auditLogRepository)

        val profileId = parseUuid(params.profileId)
        val activated = profileService.activateProfile(targetUserId, profileId)
        logger.info { "Activated profile $profileId for user $targetUserId" }
        call.respond(activated.toApi())
    }
}

private fun Route.acceptProposedProfile(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    post<org.javafreedom.kdiab.profiles.api.Paths.acceptProposedProfile> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.accept", auditLogRepository)

        val profileId = parseUuid(params.profileId)
        val activated = profileService.acceptProposedProfile(targetUserId, profileId)
        call.respond(activated.toApi())
    }
}

private fun Route.rejectProposedProfile(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    post<org.javafreedom.kdiab.profiles.api.Paths.rejectProposedProfile> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.reject", auditLogRepository)

        val profileId = parseUuid(params.profileId)
        val rejectRequest = runCatching { call.receive<RejectProfileRequest>() }.getOrNull()
        val rejected = profileService.rejectProposedProfile(targetUserId, profileId, rejectRequest?.reason)
        call.respond(rejected.toApi())
    }
}

private fun Route.deleteSegment(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    delete<org.javafreedom.kdiab.profiles.api.Paths.deleteSegment> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.delete-segment", auditLogRepository)

        val profileId = parseUuid(params.profileId)
        val segmentType = params.segmentType
        val startTime = kotlinx.datetime.LocalTime.parse(params.startTime)

        val updated = profileService.deleteSegment(targetUserId, profileId, segmentType, startTime)
        call.respond(updated.toApi())
    }
}

private fun Route.deleteProfile(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    delete<org.javafreedom.kdiab.profiles.api.Paths.deleteProfile> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.delete", auditLogRepository)

        val profileId = parseUuid(params.profileId)
        val deleted = profileService.deleteProfile(targetUserId, profileId)
        if (deleted) {
            call.respond(HttpStatusCode.NoContent)
        } else {
            throw ResourceNotFoundException("Profile not found")
        }
    }
}

private fun Route.deleteAllProfiles(profileService: ProfileService, auditLogRepository: AuditLogRepository) {
    delete<org.javafreedom.kdiab.profiles.api.Paths.deleteProfiles> { params ->
        val principal = call.principal<UserPrincipal>()
        val targetUserId = parseUuid(params.userId)

        checkWriteAccess(principal, targetUserId)
        auditIfDoctor(call, principal, targetUserId, "profiles.delete-all", auditLogRepository)

        // Idempotent: deleting when no drafts exist is still a success
        profileService.deleteAllProfiles(targetUserId)
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun checkReadAccess(
        principal: UserPrincipal?,
        targetUserId: Uuid
) {
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

private fun checkWriteAccess(
        principal: UserPrincipal?,
        targetUserId: Uuid
) {
    if (principal == null || (principal.userId != targetUserId && !principal.isAdmin())) {
        logger.warn {
            "Write access denied: principalId=${principal?.userId} " +
            "roles=${principal?.roles} " +
            "allowedPatients=${principal?.allowedPatients} " +
            "targetUserId=$targetUserId"
        }
        throw AuthorizationException("Write Access Denied")
    }
}
