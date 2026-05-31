@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.users.application.service.InvitationListResult
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation
import org.javafreedom.kdiab.users.domain.model.InvitationAction

/**
 * Request body for POST /users/{doctorId}/invitations.
 */
@Serializable
data class SendInvitationRequest(
    val patientIdentifier: String,
    val message: String? = null,
)

/**
 * Request body for PATCH /users/{patientId}/invitations/{invitationId}.
 * The action field must be "ACCEPT" or "DECLINE".
 */
@Serializable
data class RespondToInvitationRequest(
    val action: String,
) {
    fun toAction(): InvitationAction =
        runCatching { InvitationAction.valueOf(action.uppercase()) }
            .getOrElse {
                throw IllegalArgumentException(
                    "Invalid action '$action'. Must be ACCEPT or DECLINE.",
                )
            }
}

/**
 * Response body for invitation endpoints.
 * Matches the InvitationResponse schema in openapi.yaml.
 */
@Serializable
data class InvitationResponse(
    val id: String,
    val doctorId: String,
    val patientIdentifier: String,
    val patientId: String?,
    val status: String,
    val createdAt: String,
    val expiresAt: String,
    val resolvedAt: String?,
    val doctorDisplayName: String?,
    val patientDisplayName: String?,
)

// doctorDisplayName and patientDisplayName are populated by list/detail endpoints
// that resolve display names from the identity provider. The POST and PATCH responses omit them (null).
fun DoctorInvitation.toResponse(
    doctorDisplayName: String? = null,
    patientDisplayName: String? = null,
) = InvitationResponse(
    id = id.toString(),
    doctorId = doctorId.toString(),
    patientIdentifier = patientIdentifier,
    patientId = patientId?.toString(),
    status = status.name,
    createdAt = createdAt.toString(),
    expiresAt = expiresAt.toString(),
    resolvedAt = resolvedAt?.toString(),
    doctorDisplayName = doctorDisplayName,
    patientDisplayName = patientDisplayName,
)

/**
 * Paginated response for invitation list endpoints.
 */
@Serializable
data class InvitationPageResponse(
    val content: List<InvitationResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

fun InvitationListResult.toPageResponse() = InvitationPageResponse(
    content = invitations.map { invitation ->
        invitation.toResponse(
            doctorDisplayName = doctorDisplayName,
            patientDisplayName = invitation.patientId?.toString()
                ?.let { patientDisplayNames[it] },
        )
    },
    page = page,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
)
