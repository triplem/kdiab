@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.application.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.common.domain.exception.AuthorizationException
import org.javafreedom.kdiab.common.domain.exception.BusinessValidationException
import org.javafreedom.kdiab.common.domain.exception.ConflictException
import org.javafreedom.kdiab.common.domain.exception.ResourceNotFoundException
import org.javafreedom.kdiab.common.domain.model.Role
import org.javafreedom.kdiab.common.plugins.UserPrincipal
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.DoctorPatientRelation
import org.javafreedom.kdiab.users.domain.model.InvitationAction
import org.javafreedom.kdiab.users.domain.model.InvitationStatus
import org.javafreedom.kdiab.users.domain.repository.DoctorInvitationRepository
import org.javafreedom.kdiab.users.domain.repository.DoctorPatientRepository
import org.javafreedom.kdiab.users.domain.repository.IdentityProviderPort

private val logger = KotlinLogging.logger {}

private val INVITATION_TTL = 7.days
private const val MAX_PAGE_SIZE = 100

/**
 * Paginated list result for invitation list endpoints.
 * [doctorDisplayName] is null when the Keycloak profile lookup fails (best-effort),
 * and also null for admin-scoped list (cross-doctor view).
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
    private val doctorPatientRepo: DoctorPatientRepository,
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

    /**
     * Returns PENDING invitations addressed to [patientId].
     *
     * Authorization rules:
     * - PATIENT may only list their own inbox (principal.userId == patientId).
     * - ADMIN may list any patient's inbox.
     * - Any other principal gets a 403.
     */
    suspend fun listIncomingInvitations(
        principal: UserPrincipal,
        patientId: Uuid,
    ): List<DoctorInvitation> {
        authorizePatient(principal, patientId)
        val invitations = invitationRepo.findPendingByPatientId(patientId)
        logger.info { "list_incoming_invitations patient=$patientId count=${invitations.size}" }
        return invitations
    }

    /**
     * Patient accepts or declines a PENDING doctor invitation.
     *
     * Authorization rules:
     * - PATIENT may only respond to their own invitations (principal.userId == patientId).
     * - ADMIN may respond on behalf of any patient.
     * - Any other principal gets a 403.
     *
     * Business rules:
     * - Invitation must exist — otherwise 404.
     * - Invitation must belong to [patientId] — otherwise 404 (not 403, to avoid enumeration).
     * - Invitation must be in PENDING status — otherwise 409.
     *
     * ACCEPT path (compensating transaction):
     * 1. Update invitation status to ACCEPTED.
     * 2. Create DoctorPatientRelation and sync Keycloak.
     * 3. On Keycloak failure: roll back invitation to PENDING; log and rethrow.
     *
     * DECLINE path: update status to DECLINED; no doctor-patient link is created.
     *
     * @return the updated [DoctorInvitation] with the new status and [DoctorInvitation.resolvedAt] set.
     */
    suspend fun respondToInvitation(
        principal: UserPrincipal,
        patientId: Uuid,
        invitationId: Uuid,
        action: InvitationAction,
    ): DoctorInvitation {
        authorizePatient(principal, patientId)

        val invitation = invitationRepo.findById(invitationId)
            ?: throw ResourceNotFoundException("Invitation $invitationId not found")

        if (invitation.patientId != patientId) {
            throw ResourceNotFoundException("Invitation $invitationId not found")
        }

        if (invitation.status != InvitationStatus.PENDING) {
            throw ConflictException(
                "Invitation $invitationId cannot be responded to: status is ${invitation.status}",
            )
        }

        val now = Clock.System.now()
        return when (action) {
            InvitationAction.ACCEPT -> acceptInvitation(invitation, patientId, now)
            InvitationAction.DECLINE -> declineInvitation(invitation, now)
        }
    }

    private suspend fun acceptInvitation(
        invitation: DoctorInvitation,
        patientId: Uuid,
        now: kotlin.time.Instant,
    ): DoctorInvitation {
        invitationRepo.updateStatus(invitation.id, InvitationStatus.ACCEPTED, now)

        val relation = DoctorPatientRelation(
            doctorId = invitation.doctorId,
            patientId = patientId,
            createdAt = now,
        )
        doctorPatientRepo.save(relation)

        try {
            syncAllowedPatients(invitation.doctorId)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error(e) {
                "invitation_accept kc_sync_failed rolling_back" +
                    " invitation=${invitation.id} doctor=${invitation.doctorId} patient=$patientId"
            }
            runCatching { doctorPatientRepo.delete(invitation.doctorId, patientId) }.onFailure { re ->
                logger.error(re) {
                    "invitation_accept rollback_failed invitation=${invitation.id}" +
                        " doctor=${invitation.doctorId} patient=$patientId"
                }
            }
            runCatching {
                invitationRepo.updateStatus(invitation.id, InvitationStatus.PENDING, null)
            }.onFailure { re ->
                logger.error(re) {
                    "invitation_accept status_rollback_failed invitation=${invitation.id}"
                }
            }
            throw e
        }

        logger.info {
            "invitation_accepted invitation=${invitation.id} doctor=${invitation.doctorId} patient=$patientId"
        }
        return invitation.copy(status = InvitationStatus.ACCEPTED, resolvedAt = now)
    }

    private suspend fun declineInvitation(
        invitation: DoctorInvitation,
        now: kotlin.time.Instant,
    ): DoctorInvitation {
        invitationRepo.updateStatus(invitation.id, InvitationStatus.DECLINED, now)
        logger.info {
            "invitation_declined invitation=${invitation.id} doctor=${invitation.doctorId}"
        }
        return invitation.copy(status = InvitationStatus.DECLINED, resolvedAt = now)
    }

    private suspend fun syncAllowedPatients(doctorId: Uuid) {
        val patientIds = doctorPatientRepo.findAllPatientIdsByDoctorId(doctorId).map { it.toString() }
        identityProvider.updateUserAttributes(doctorId, mapOf("allowed_patients" to patientIds))
    }

    /**
     * Expires all PENDING invitations whose [DoctorInvitation.expiresAt] is before [cutoff].
     *
     * This is an internal housekeeping operation called by
     * [org.javafreedom.kdiab.users.application.jobs.InvitationExpiryJob] on a scheduled basis.
     * No principal is required.
     *
     * @return the number of invitations that were transitioned to EXPIRED.
     */
    suspend fun expireOldInvitations(cutoff: Instant = Clock.System.now()): Int {
        val count = invitationRepo.expireBefore(cutoff)
        if (count > 0) logger.info { "invitation_expiry expired count=$count" }
        return count
    }

    /**
     * Cancels a PENDING invitation belonging to [doctorId].
     *
     * Authorization rules:
     * - ADMIN may cancel on behalf of any doctorId.
     * - DOCTOR may only cancel their own invitations (principal.userId == doctorId).
     * - Any other principal gets a 403.
     *
     * Business rules:
     * - Invitation must exist and belong to [doctorId] — otherwise 404.
     * - Invitation must be in PENDING status — otherwise 409.
     * - Status is updated to CANCELLED (soft delete) with resolvedAt timestamp.
     */
    suspend fun cancelInvitation(
        principal: UserPrincipal,
        doctorId: Uuid,
        invitationId: Uuid,
    ) {
        authorizeDoctor(principal, doctorId)

        val invitation = invitationRepo.findById(invitationId)
            ?: throw ResourceNotFoundException("Invitation $invitationId not found")

        if (invitation.doctorId != doctorId) {
            throw ResourceNotFoundException("Invitation $invitationId not found")
        }

        if (invitation.status != InvitationStatus.PENDING) {
            throw ConflictException(
                "Invitation $invitationId cannot be cancelled: status is ${invitation.status}",
            )
        }

        val now = Clock.System.now()
        invitationRepo.updateStatus(invitationId, InvitationStatus.CANCELLED, now)
        logger.info {
            "invitation_cancelled doctor=$doctorId invitation=$invitationId"
        }
    }

    /**
     * Lists all invitations across all doctors (admin-only, paginated, optionally filtered by status).
     *
     * Authorization rules:
     * - Only ADMIN may call this endpoint.
     * - Any other principal gets a 403.
     */
    suspend fun listAllInvitations(
        principal: UserPrincipal,
        status: InvitationStatus?,
        page: Int,
        size: Int,
    ): InvitationListResult {
        if (!principal.isAdmin()) {
            throw AuthorizationException("Only admins may list all invitations")
        }
        val clampedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val safePageIndex = page.coerceAtLeast(0)
        val offset = safePageIndex.toLong() * clampedSize
        val invitations = invitationRepo.findAll(status, clampedSize, offset)
        val totalElements = invitationRepo.countAll(status)
        val totalPages = if (totalElements == 0L) 0
        else ((totalElements + clampedSize - 1) / clampedSize).toInt()
        val patientDisplayNames = resolvePatientDisplayNames(invitations)
        logger.info {
            "admin_list_all_invitations status=$status page=$safePageIndex size=$clampedSize total=$totalElements"
        }
        return InvitationListResult(
            invitations = invitations,
            doctorDisplayName = null,
            patientDisplayNames = patientDisplayNames,
            page = safePageIndex,
            size = clampedSize,
            totalElements = totalElements,
            totalPages = totalPages,
        )
    }

    /**
     * Admin cancels any PENDING invitation regardless of which doctor owns it.
     *
     * Authorization rules:
     * - Only ADMIN may call this endpoint.
     * - Any other principal gets a 403.
     *
     * Business rules:
     * - Invitation must exist — otherwise 404.
     * - Invitation must be in PENDING status — otherwise 409.
     * - Status is updated to CANCELLED with a resolvedAt timestamp.
     */
    suspend fun adminCancelInvitation(
        principal: UserPrincipal,
        invitationId: Uuid,
    ) {
        if (!principal.isAdmin()) {
            throw AuthorizationException("Only admins may use the admin cancel endpoint")
        }
        val invitation = invitationRepo.findById(invitationId)
            ?: throw ResourceNotFoundException("Invitation $invitationId not found")
        if (invitation.status != InvitationStatus.PENDING) {
            throw ConflictException(
                "Invitation $invitationId cannot be cancelled: status is ${invitation.status}",
            )
        }
        val now = Clock.System.now()
        invitationRepo.updateStatus(invitationId, InvitationStatus.CANCELLED, now)
        logger.info {
            "admin_invitation_cancelled admin=${principal.userId} invitation=$invitationId" +
                " doctor=${invitation.doctorId}"
        }
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
                "Only the doctor themselves or an admin may perform this action on behalf of doctor $doctorId",
            )
        }
    }

    private fun authorizePatient(principal: UserPrincipal, patientId: Uuid) {
        when {
            principal.isAdmin() -> Unit
            principal.userId == patientId -> Unit
            else -> throw AuthorizationException(
                "Only the patient themselves or an admin may access invitations for patient $patientId",
            )
        }
    }
}
