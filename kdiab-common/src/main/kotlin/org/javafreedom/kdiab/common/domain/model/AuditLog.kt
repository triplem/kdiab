@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.common.domain.model

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class AuditLog(
    val id: Uuid,
    val doctorId: Uuid,
    val patientId: Uuid,
    val action: String,
    val occurredAt: Instant,
    val ipAddress: String?,
    val userAgent: String?,
    val detail: String? = null,
)
