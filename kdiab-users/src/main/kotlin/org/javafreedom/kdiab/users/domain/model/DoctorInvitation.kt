@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.users.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class DoctorInvitation(
    val id: Uuid,
    val doctorId: Uuid,
    val patientIdentifier: String,
    val patientId: Uuid?,
    val status: InvitationStatus,
    val message: String?,
    val createdAt: Instant,
    val expiresAt: Instant,
    val resolvedAt: Instant?,
)
