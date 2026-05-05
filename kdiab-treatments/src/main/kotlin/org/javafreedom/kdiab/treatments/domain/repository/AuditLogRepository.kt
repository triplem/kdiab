@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package org.javafreedom.kdiab.treatments.domain.repository

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.javafreedom.kdiab.treatments.domain.model.AuditLog

interface AuditLogRepository {
    suspend fun save(entry: AuditLog)
    suspend fun findByPatientId(patientId: Uuid, from: Instant, to: Instant): List<AuditLog>
}
