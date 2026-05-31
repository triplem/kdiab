@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.InvitationStatus
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort

private val logger = KotlinLogging.logger {}

private val INVITATION_TTL = 7.days
private const val MAX_PAGE_SIZE = 100

/**
 * Paginated list result for the GET /users/{doctorId}/invitations endpoint.
 * [doctorDisplayName] is null when the Keycloak profile lookup fails (best-effort).
 * [patientDisplayNames] maps patientId string → display name (null on lookup failure).
 */
data class InvitationListResult(
    val invitations: List<DoctorInvitation>,
    val doctorDisplayName: String?,
    val patientDisplayNames: Map<String, String>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

class InvitationService(
    private val invitationRepo: DoctorInvitationRepository,
    private val identityProvider: IdentityProviderPort,
) {
    /**
     * Sends an invitation from a doctor to a patient identified by email or username.
     *
     * Authorization rules:
     * - ADMIN may act on behalf of any doctorId.
     * - DOCTOR may only send on their own behalf (principal.userId == doctorId).
     * - Any other principal gets a 403.
     */
    suspend fun sendInvitation(
        principal: UserPrincipal,
        doctorId: Uuid,
        patientIdentifier: String,
        message: String?,
    ): DoctorInvitation {
        authorizeDoctor(principal, doctorId)

        val doctorRoles = identityProvider.getUserRoles(doctorId)
        if (Role.DOCTOR !in doctorRoles) {
            throw BusinessValidationException("User $doctorId does not have the DOCTOR role")
        }

        val patientId = identityProvider.findUserByIdentifier(patientIdentifier)
            ?: throw BusinessValidationException("Patient identifier could not be resolved")

        val patientRoles = identityProvider.getUserRoles(patientId)
        if (Role.PATIENT !in patientRoles) {
            throw BusinessValidationException("Resolved user does not have the PATIENT role")
        }

        if (invitationRepo.existsPendingByDoctorAndPatient(doctorId, patientId)) {
            throw ConflictException("A pending invitation already exists for this doctor-patient pair")
        }

        val now = Clock.System.now()
        val invitation = DoctorInvitation(
            id = Uuid.random(),
            doctorId = doctorId,
            patientIdentifier = patientIdentifier,
            patientId = patientId,
            status = InvitationStatus.PENDING,
            message = message,
            createdAt = now,
            expiresAt = now + INVITATION_TTL,
            resolvedAt = null,
        )

        val saved = invitationRepo.save(invitation)
        logger.info {
            "invitation_sent doctor=$doctorId patient=$patientId invitation=${saved.id}"
        }
        return saved
    }

    /**
     * Returns a paginated list of invitations sent by [doctorId].
     *
     * Authorization rules:
     * - ADMIN may list for any doctorId.
     * - DOCTOR may only list their own invitations (principal.userId == doctorId).
     * - Any other principal gets a 403.
     *
     * Display names are resolved best-effort: a failed Keycloak lookup produces null names,
     * never a 500 error.
     */
    suspend fun listDoctorInvitations(
        principal: UserPrincipal,
        doctorId: Uuid,
        statuses: Set<InvitationStatus>,
        page: Int,
        size: Int,
    ): InvitationListResult {
        authorizeDoctor(principal, doctorId)

        val clampedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val offset = page.toLong() * clampedSize
        val total = invitationRepo.countByDoctorId(doctorId, statuses)
        val invitations = invitationRepo.findByDoctorId(doctorId, statuses, clampedSize, offset)

        val doctorDisplayName = resolveDisplayName(doctorId)
        val patientDisplayNames = resolvePatientDisplayNames(invitations)

        val totalPages = if (total == 0L) 0 else ((total + clampedSize - 1) / clampedSize).toInt()

        logger.info {
            "list_doctor_invitations doctor=$doctorId page=$page size=$clampedSize total=$total"
        }
        return InvitationListResult(
            invitations = invitations,
            doctorDisplayName = doctorDisplayName,
            patientDisplayNames = patientDisplayNames,
            page = page,
            size = clampedSize,
            totalElements = total,
            totalPages = totalPages,
        )
    }

    private suspend fun resolveDisplayName(userId: Uuid): String? =
        runCatching { identityProvider.getUserProfile(userId) }
            .onFailure { logger.warn { "display_name_lookup_failed userId=$userId reason=${it.message}" } }
            .getOrNull()
            ?.let { profile ->
                listOfNotNull(profile.firstName, profile.lastName)
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }
            }

    private suspend fun resolvePatientDisplayNames(
        invitations: List<DoctorInvitation>,
    ): Map<String, String> {
        val uniquePatientIds = invitations.mapNotNull { it.patientId }.toSet()
        return uniquePatientIds.mapNotNull { patientId ->
            resolveDisplayName(patientId)?.let { patientId.toString() to it }
        }.toMap()
    }

    private fun authorizeDoctor(principal: UserPrincipal, doctorId: Uuid) {
        when {
            principal.isAdmin() -> Unit
            principal.isDoctor() && principal.userId == doctorId -> Unit
            else -> throw AuthorizationException(
                "Only the doctor themselves or an admin may send invitations on behalf of doctor $doctorId",
            )
        }
    }
}
