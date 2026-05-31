@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.adapters.inbound.web

import kotlinx.serialization.Serializable
import org.javafreedom.kdiab.users.domain.model.DoctorInvitation

/**
 * Request body for POST /users/{doctorId}/invitations.
 */
@Serializable
data class SendInvitationRequest(
    val patientIdentifier: String,
    val message: String? = null,
)

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

// doctorDisplayName and patientDisplayName are populated by future list/detail endpoints
// that resolve display names from the identity provider. The POST response omits them (null).
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
